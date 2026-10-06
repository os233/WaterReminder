package com.example.waterreminder.data.remote

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `version.json` 逐字段校验规则（AGENTS.md 第 4 节契约）：
 * 缺失或非法一律 Failed、整数 versionCode 比较、HTTPS-only、sha256 仅认 64 位十六进制。
 */
class ParseVersionJsonTest {

    private val sha64 = "a".repeat(64)

    private val validJson = """
        {
          "versionCode": 12,
          "versionName": "0.2.0",
          "apkUrl": "https://github.com/os233/WaterReminder/releases/download/v0.2.0/WaterReminder_v0.2.0_release.apk",
          "sha256": "$sha64",
          "changelog": "修复若干问题",
          "forceUpdate": false
        }
    """.trimIndent()

    private fun validJsonWithSha(sha: String): String = """
        {
          "versionCode": 12,
          "versionName": "0.2.0",
          "apkUrl": "https://a/v0.2.0.apk",
          "sha256": "$sha",
          "forceUpdate": false
        }
    """.trimIndent()

    @Test
    fun `字段齐全且版本更高时返回Available并逐字段映射`() {
        val result = parseVersionJson(validJson, currentVersionCode = 11)
        val info = (result as UpdateCheckResult.Available).info
        assertEquals(12, info.versionCode)
        assertEquals("0.2.0", info.versionName)
        assertEquals(
            "https://github.com/os233/WaterReminder/releases/download/v0.2.0/WaterReminder_v0.2.0_release.apk",
            info.apkUrl
        )
        assertEquals("修复若干问题", info.changelog)
        assertEquals(sha64, info.sha256)
        assertEquals(false, info.forceUpdate)
    }

    @Test
    fun `versionCode不高于本机时返回UpToDate`() {
        assertEquals(UpdateCheckResult.UpToDate, parseVersionJson(validJson, currentVersionCode = 12))
        assertEquals(UpdateCheckResult.UpToDate, parseVersionJson(validJson, currentVersionCode = 13))
    }

    @Test
    fun `versionCode缺失_非正数或字符串类型判为Failed`() {
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionName": "0.2.0", "apkUrl": "https://a/b.apk"}""", 0)
        )
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionCode": 0, "versionName": "0.2.0", "apkUrl": "https://a/b.apk"}""", 0)
        )
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionCode": -1, "versionName": "0.2.0", "apkUrl": "https://a/b.apk"}""", 0)
        )
        // 整数比较依据，不能拿字符串猜
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionCode": "12", "versionName": "0.2.0", "apkUrl": "https://a/b.apk"}""", 0)
        )
    }

    @Test
    fun `versionName缺失或空白判为Failed`() {
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionCode": 12, "apkUrl": "https://a/b.apk"}""", 11)
        )
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionCode": 12, "versionName": "   ", "apkUrl": "https://a/b.apk"}""", 11)
        )
    }

    @Test
    fun `apkUrl缺失或非HTTPS判为Failed`() {
        assertEquals(
            UpdateCheckResult.Failed,
            parseVersionJson("""{"versionCode": 12, "versionName": "0.2.0"}""", 11)
        )
        for (url in listOf(
            "http://github.com/a/b.apk",
            "ftp://github.com/a/b.apk",
            "//github.com/a/b.apk"
        )) {
            assertEquals(
                "apkUrl=$url 应拒绝",
                UpdateCheckResult.Failed,
                parseVersionJson("""{"versionCode": 12, "versionName": "0.2.0", "apkUrl": "$url"}""", 11)
            )
        }
    }

    @Test
    fun `changelog缺失时映射为空串`() {
        val j = """{"versionCode": 12, "versionName": "0.2.0", "apkUrl": "https://a/b.apk"}"""
        val info = (parseVersionJson(j, 11) as UpdateCheckResult.Available).info
        assertEquals("", info.changelog)
    }

    @Test
    fun `sha256为64位十六进制时原样保留_含大写`() {
        val upper = "A1F".repeat(21) + "C" // 恰好 64 位，全为十六进制字符
        val info = (parseVersionJson(validJsonWithSha(upper), 11) as UpdateCheckResult.Available).info
        assertEquals(upper, info.sha256)
    }

    @Test
    fun `sha256长度不对或含非十六进制字符时按无摘要处理`() {
        for (bad in listOf(
            "a".repeat(63), // 少一位
            "a".repeat(65), // 多一位
            "g".repeat(64), // 非十六进制
            ""              // 空串
        )) {
            val info = (parseVersionJson(validJsonWithSha(bad), 11) as UpdateCheckResult.Available).info
            assertNull("sha256=\"$bad\" 应视为缺失", info.sha256)
        }
    }

    @Test
    fun `forceUpdate缺失或类型非法按false`() {
        val missing = """{"versionCode": 12, "versionName": "0.2.0", "apkUrl": "https://a/b.apk"}"""
        assertFalse((parseVersionJson(missing, 11) as UpdateCheckResult.Available).info.forceUpdate)

        // 字符串 "true" 不是布尔，不能猜
        val wrongType =
            """{"versionCode": 12, "versionName": "0.2.0", "apkUrl": "https://a/b.apk", "forceUpdate": "true"}"""
        assertFalse((parseVersionJson(wrongType, 11) as UpdateCheckResult.Available).info.forceUpdate)

        val explicit = validJsonWithSha(sha64).replace("\"forceUpdate\": false", "\"forceUpdate\": true")
        assertTrue((parseVersionJson(explicit, 11) as UpdateCheckResult.Available).info.forceUpdate)
    }

    @Test
    fun `未知新增字段被忽略_兼容契约只增不改`() {
        val extended = """
            {
              "someFutureField": {"nested": [1, 2]},
              "versionCode": 12,
              "versionName": "0.2.0",
              "apkUrl": "https://a/b.apk",
              "forceUpdate": false
            }
        """.trimIndent()
        val result = parseVersionJson(extended, currentVersionCode = 11)
        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun `坏JSON与非对象结构向上抛出_由调用方catch兜底`() {
        assertThrows(JsonSyntaxException::class.java) { parseVersionJson("not json", 0) }
        assertThrows(IllegalStateException::class.java) { parseVersionJson("""[1,2]""", 0) }
    }
}
