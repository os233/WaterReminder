package com.example.waterreminder.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** sha256Of 的流式读取与十六进制编码：用标准向量与一次性摘要交叉验证。 */
class Sha256OfFileTest {

    private fun writeFile(bytes: ByteArray): File =
        File.createTempFile("sha256-test", ".bin").apply { writeBytes(bytes) }

    @Test
    fun `空文件摘要与标准向量一致`() {
        val f = writeFile(ByteArray(0))
        try {
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                sha256Of(f)
            )
        } finally {
            f.delete()
        }
    }

    @Test
    fun `abc的摘要与标准向量一致`() {
        val f = writeFile("abc".toByteArray())
        try {
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                sha256Of(f)
            )
        } finally {
            f.delete()
        }
    }

    @Test
    fun `跨读取缓冲区的大文件与一次性摘要一致`() {
        // 读缓冲是 8KB，取 2.5 倍：跨两次 update 加一次收尾
        val bytes = ByteArray(8 * 1024 * 2 + 2048) { 'a'.code.toByte() }
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val f = writeFile(bytes)
        try {
            assertEquals(expected, sha256Of(f))
        } finally {
            f.delete()
        }
    }

    @Test
    fun `读不到的文件返回null而不是抛异常`() {
        // fail-closed 契约：摘要拿不到时按 null 处理，绝不拿错的值去比较
        assertNull(sha256Of(File("no-such-file-${System.nanoTime()}.bin")))
    }
}
