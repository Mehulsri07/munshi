package com.opuscube.munshi.brain

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ModelDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer().apply { start() }
    @After fun down() = server.shutdown()

    private val body = "0123456789abcdef".repeat(64).toByteArray()      // 1 KB "model"
    private fun hex(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun model(sha: String = hex(body)) = ModelFile("m.litertlm", server.url("/m.litertlm").toString(), sha, body.size.toLong())

    @Test fun `downloads, reports progress and checks the hash`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        val dest = File(tmp.root, "m.litertlm")
        var last = 0L
        assertTrue(ModelDownloader(OkHttpClient()).fetch(model(), dest) { done, _ -> last = done })
        assertArrayEquals(body, dest.readBytes())
        assertEquals(body.size.toLong(), last)
        assertFalse(File(dest.path + ".part").exists())
    }

    @Test fun `resumes a partial file with a range request`() = runTest {
        val dest = File(tmp.root, "m.litertlm")
        File(dest.path + ".part").writeBytes(body.copyOfRange(0, 100))
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(body.copyOfRange(100, body.size))))
        assertTrue(ModelDownloader(OkHttpClient()).fetch(model(), dest) { _, _ -> })
        assertEquals("bytes=100-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(body, dest.readBytes())
    }

    @Test fun `a wrong hash or a server error leaves no model behind`() = runTest {
        val dest = File(tmp.root, "m.litertlm")
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        assertFalse(ModelDownloader(OkHttpClient()).fetch(model(sha = "0".repeat(64)), dest) { _, _ -> })
        assertFalse(dest.exists()); assertFalse(File(dest.path + ".part").exists())
        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(ModelDownloader(OkHttpClient()).fetch(model(), dest) { _, _ -> })
        assertFalse(dest.exists())
    }
}
