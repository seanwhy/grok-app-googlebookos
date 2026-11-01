package com.grokapp.googlebookos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeHelperTest {

    @Test
    fun testNormalizeUrl() {
        assertEquals("http://localhost:3000", ChromeHelper.normalizeUrl("0.0.0.0:3000"))
        assertEquals("http://localhost:5173/dashboard", ChromeHelper.normalizeUrl("http://0.0.0.0:5173/dashboard"))
        assertEquals("http://localhost:8080", ChromeHelper.normalizeUrl("localhost:8080"))
        assertEquals("https://google.com", ChromeHelper.normalizeUrl("https://google.com"))
        assertEquals("http://127.0.0.1:8088/preview/test.html", ChromeHelper.normalizeUrl("http://127.0.0.1:8088/preview/test.html"))
    }

    @Test
    fun testExtractUrls() {
        val log = """
            > vite dev
            VITE v5.2.0  ready in 240 ms
            ➜  Local:   http://localhost:5173/
            ➜  Network: use --host to expose
        """.trimIndent()
        val urls = ChromeHelper.extractUrls(log)
        assertTrue(urls.contains("http://localhost:5173/"))
    }

    @Test
    fun testExtractLocalhostWithoutScheme() {
        val text = "Server listening on localhost:8000 for connections"
        val urls = ChromeHelper.extractUrls(text)
        assertTrue(urls.any { it.contains("localhost:8000") })
    }

    @Test
    fun testIsHtmlOrWebCode() {
        assertTrue(ChromeHelper.isHtmlOrWebCode("html", "<h1>Hello</h1>"))
        assertTrue(ChromeHelper.isHtmlOrWebCode("htm", "<div class='container'>Test</div>"))
        assertTrue(ChromeHelper.isHtmlOrWebCode("", "<!DOCTYPE html><html><body>Test</body></html>"))
        assertTrue(ChromeHelper.isHtmlOrWebCode("xml", "<html><head><title>Page</title></head></html>"))
        assertTrue(ChromeHelper.isHtmlOrWebCode("svg", "<svg width='100' height='100'><circle cx='50' cy='50' r='40'/></svg>"))
        assertTrue(ChromeHelper.isHtmlOrWebCode("xml", "<svg viewBox='0 0 100 100'><path d='M10 10'/></svg>"))
        
        assertFalse(ChromeHelper.isHtmlOrWebCode("kotlin", "fun main() { println(\"test\") }"))
        assertFalse(ChromeHelper.isHtmlOrWebCode("python", "import sys\nprint('hello')"))
        assertFalse(ChromeHelper.isHtmlOrWebCode("bash", "npm run dev"))
    }

    @Test
    fun testIsLocalWebUrl() {
        assertTrue(ChromeHelper.isLocalWebUrl("http://localhost:3000"))
        assertTrue(ChromeHelper.isLocalWebUrl("http://127.0.0.1:8088/preview/abc.html"))
        assertTrue(ChromeHelper.isLocalWebUrl("http://0.0.0.0:5000"))
        assertFalse(ChromeHelper.isLocalWebUrl("https://github.com/google/grok"))
        assertFalse(ChromeHelper.isLocalWebUrl("https://x.ai"))
    }
}
