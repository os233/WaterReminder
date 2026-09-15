package com.example.waterreminder.data.remote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** 一次检查的结果。手动检查要区分「确实最新」和「没查到」，所以不用 null 表示两者。 */
sealed interface UpdateCheckResult {
    /** 有可用更新 */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** 已是最新；被节流跳过时也返回这个（自动检查对两者都是「不提示」） */
    data object UpToDate : UpdateCheckResult

    /** 没查到：断网、HTTP 非 2xx、JSON 结构不符、字段非法 */
    data object Failed : UpdateCheckResult
}

/**
 * 更新检查与安装。
 *
 * 版本信息读的是 GitHub Pages 上的 `version.json` —— 一个由 CI 在发版时自动生成的静态
 * 发布 Manifest，**不是** GitHub Releases API。
 *
 * 为什么不用 API：
 *  - 未认证的 API 只有 60 次/小时，且配额**按出口 IP 共享**，公司 / 校园网 / 运营商 NAT
 *    下很容易被别人的请求用光，拿到 403 之后更新检查就静默失败了；
 *  - Release 的说明就是 `version.json` 里的 changelog（CI 建 Release 时灌进去的），
 *    绕道 API 拿不到任何 `version.json` 里没有的东西，还要多吃一次配额；
 *  - 静态文件在 CDN 上，没有配额，App 也不必猜 API 的响应结构。
 *
 * 解析对每个字段都显式校验，缺失或非法一律返回 [UpdateCheckResult.Failed] —— 不用 Gson
 * 直接反序列化，因为那样缺字段会被静默填成 0/null，表现为「永远没有更新」。
 */
class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()

    companion object {
        private const val VERSION_JSON_URL =
            "https://os233.github.io/WaterReminder/version.json"

        private const val PREFS = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check_at"

        /** 自动检查的最小间隔。静态文件没有配额限制，但也没必要每次启动都打 */
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

        /** SHA-256 十六进制摘要的长度 */
        private const val SHA256_HEX_LENGTH = 64
    }

    /**
     * 检查是否有新版本。
     *
     * 距上次成功检查不足 [CHECK_INTERVAL_MS] 时直接返回 [UpdateCheckResult.UpToDate]；
     * [force] 为 true（手动检查）则忽略节流。
     * 任何失败都只返回 [UpdateCheckResult.Failed]，不抛异常 ——
     * 更新检查失败不等于 App 运行失败。
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                return@withContext UpdateCheckResult.UpToDate
            }

            val request = Request.Builder()
                .url(VERSION_JSON_URL)
                .header("User-Agent", "WaterReminder")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateCheckResult.Failed
                // 只在真的问到了才记时间，否则一次断网会把重试也压掉 12 小时
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                response.body?.string() ?: return@withContext UpdateCheckResult.Failed
            }

            parseVersionJson(body)
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateCheckResult.Failed
        }
    }

    /** 解析 `version.json`。字段缺失或非法一律判为失败，宁可说「没查到」也不猜。 */
    private fun parseVersionJson(json: String): UpdateCheckResult {
        val root = JsonParser.parseString(json).asJsonObject

        val versionCode = root.intOrNull("versionCode") ?: return UpdateCheckResult.Failed
        if (versionCode <= 0) return UpdateCheckResult.Failed

        val versionName = root.stringOrNull("versionName")?.takeIf { it.isNotBlank() }
            ?: return UpdateCheckResult.Failed

        val apkUrl = root.stringOrNull("apkUrl") ?: return UpdateCheckResult.Failed
        // 更新包只允许走 HTTPS，拒绝任何明文下载
        if (!apkUrl.startsWith("https://")) return UpdateCheckResult.Failed

        if (versionCode <= currentVersionCode()) return UpdateCheckResult.UpToDate

        return UpdateCheckResult.Available(
            UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                changelog = root.stringOrNull("changelog").orEmpty(),
                // 格式不对就当没有摘要，下载后不校验 —— 但绝不会拿一个错的值去校验
                sha256 = root.stringOrNull("sha256")
                    ?.takeIf { it.length == SHA256_HEX_LENGTH && it.all(Char::isHexDigit) },
                forceUpdate = root.booleanOrNull("forceUpdate") ?: false
            )
        )
    }

    /** 装机版本号；API 28+ 用 longVersionCode，更低版本回退到已废弃的 versionCode。 */
    private fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun checkInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    /**
     * 下载并安装更新。[expectedSha256] 非空时，下载完成后先校验文件摘要，
     * 不一致就删除安装包并提示重试，不会把它交给安装器。
     */
    fun downloadAndInstall(apkUrl: String, expectedSha256: String? = null) {
        val fileName = "waterreminder_update.apk"
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadDir, fileName).delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("喝水提醒更新")
            setDescription("正在下载新版本...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id != downloadId) return

                try {
                    context.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                }

                // 通过 DownloadManager 查询真实的下载结果，而不是只看文件是否存在
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx >= 0 && cursor.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, "下载失败，请稍后重试", Toast.LENGTH_LONG).show()
                        return
                    }
                }

                val file = File(downloadDir, fileName)
                if (!file.exists()) return

                if (expectedSha256 != null && !expectedSha256.equals(sha256Of(file), ignoreCase = true)) {
                    file.delete()
                    Toast.makeText(context, "安装包校验失败，已删除，请重试", Toast.LENGTH_LONG).show()
                    return
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(installIntent)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** 计算文件的 SHA-256（小写十六进制）；读失败返回 null。 */
    private fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** 取 JSON 字段的字符串值；字段缺失或是 null 时返回 null（而不是抛异常）。 */
private fun JsonObject.stringOrNull(key: String): String? {
    val element = get(key) ?: return null
    return if (element.isJsonPrimitive) element.asString else null
}

/** 取 JSON 字段的整数值；字段缺失、是 null 或不是数字时返回 null（而不是当成 0）。 */
private fun JsonObject.intOrNull(key: String): Int? {
    val element = get(key) ?: return null
    if (!element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    return if (primitive.isNumber) primitive.asInt else null
}

/** 取 JSON 字段的布尔值；字段缺失、是 null 或不是布尔时返回 null。 */
private fun JsonObject.booleanOrNull(key: String): Boolean? {
    val element = get(key) ?: return null
    if (!element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    return if (primitive.isBoolean) primitive.asBoolean else null
}

/** `Char.isDigit()` 只认 0-9 和 Unicode 数字，摘要得限定 ASCII 十六进制字符。 */
private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
