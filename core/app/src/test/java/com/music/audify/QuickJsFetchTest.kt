package com.music.audify

import com.music.audify.data.sources.module.QuickJsExecutor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class QuickJsFetchTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetch transport preserves binary response bytes and response headers`() {
        val bytes = byteArrayOf(0, 1, 0x7f, 0x80.toByte(), 0xff.toByte())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Range", "bytes 0-4/10")
                .setBody(okio.Buffer().write(bytes)),
        )

        val response = QuickJsExecutor.fetchUrlSync(
            url = server.url("/binary").toString(),
            method = "GET",
            headersJson = "{}",
            body = null,
        )

        assertEquals(206, response.status)
        assertEquals("OK", response.statusText)
        assertEquals("application/octet-stream", response.headers["content-type"])
        assertEquals("bytes 0-4/10", response.headers["content-range"])
        assertArrayEquals(bytes, response.body)
    }

    @Test
    fun `fetch transport retains response text using declared charset`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain; charset=ISO-8859-1")
                .setBody(okio.Buffer().write("caf\u00e9".toByteArray(Charsets.ISO_8859_1))),
        )

        val response = QuickJsExecutor.fetchUrlSync(
            url = server.url("/text").toString(),
            method = "GET",
            headersJson = "{}",
            body = null,
        )

        assertEquals("caf\u00e9", response.bodyText)
    }
}
