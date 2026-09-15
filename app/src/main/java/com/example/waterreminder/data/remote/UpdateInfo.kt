package com.example.waterreminder.data.remote

/**
 * 一次可用的更新，由 GitHub Pages 上的 `version.json`（CI 在发版时自动生成的发布 Manifest）解析而来。
 *
 * ⚠️ 这些字段与 `version.json` 的顶层字段一一对应，是一份**兼容契约**：
 * 已发布的 1.4.0 也用 Gson 按顶层字段反序列化这个文件。所以只能**新增**字段 ——
 * 改名、删字段、或把它们挪进嵌套对象，都会让老客户端拿到 0/null，
 * 表现为「永远没有更新」这种没有任何报错的静默失效。
 */
data class UpdateInfo(
    /** 机器比较依据：直接与装机版本的 versionCode 比大小，不解析 versionName 字符串 */
    val versionCode: Int,
    /** 给人看的版本号，如 "1.5.0" */
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    /** APK 的 SHA-256（小写十六进制）；缺失时为 null，此时下载后不做摘要校验 */
    val sha256: String? = null,
    val forceUpdate: Boolean = false
)
