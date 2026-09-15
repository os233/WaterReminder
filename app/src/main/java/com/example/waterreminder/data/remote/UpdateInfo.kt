package com.example.waterreminder.data.remote

/** 一次可用的更新，由 GitHub Releases API 的 latest release 解析而来。 */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    /** Release asset 的 SHA-256（GitHub 的 digest 字段，已去掉 `sha256:` 前缀）；取不到时为 null */
    val sha256: String? = null,
    val forceUpdate: Boolean = false
)

/**
 * 按数字段比较版本名，避免字符串比较把 "1.10.0" 判成小于 "1.9.0"。
 * 返回 >0 表示 [a] 更新，0 表示相同，<0 表示更旧；缺失或非数字段按 0 处理。
 */
internal fun compareVersionNames(a: String, b: String): Int {
    val left = a.trim().removePrefix("v").split('.')
    val right = b.trim().removePrefix("v").split('.')
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val r = right.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (l != r) return l - r
    }
    return 0
}
