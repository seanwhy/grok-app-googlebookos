package com.grokapp.googlebookos.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ChromeHelper {

    private const val CHROME_PACKAGE = "com.android.chrome"

    /**
     * Open a URL in Google Chrome (or system browser fallback)
     */
    fun openUrl(context: Context, rawUrl: String): Boolean {
        val normalized = normalizeUrl(rawUrl)
        if (normalized.isBlank()) return false

        val uri = Uri.parse(normalized)

        // 1. Try specifically with Google Chrome
        try {
            val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(CHROME_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chromeIntent)
            return true
        } catch (_: Exception) {
            // Chrome not installed or restricted
        }

        // 2. Fallback to default browser
        try {
            val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    /**
     * Normalizes a URL:
     * - Replaces 0.0.0.0 with localhost
     * - Prepend http:// if scheme is missing
     */
    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return ""

        if (url.startsWith("http://0.0.0.0")) {
            url = url.replaceFirst("http://0.0.0.0", "http://localhost")
        } else if (url.startsWith("https://0.0.0.0")) {
            url = url.replaceFirst("https://0.0.0.0", "https://localhost")
        } else if (url.startsWith("0.0.0.0")) {
            url = "http://localhost" + url.removePrefix("0.0.0.0")
        } else if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            url = "http://$url"
        }

        return url
    }

    /**
     * Extracts all URLs and local ports from text (logs, messages, tool output).
     */
    fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val results = LinkedHashSet<String>()

        // 1. Full http/https URLs: https?://[^\s)\]>"']+
        val fullUrlRegex = "https?://[^\\s)\\]>\"']+".toRegex(RegexOption.IGNORE_CASE)
        for (match in fullUrlRegex.findAll(text)) {
            var raw = match.value.trim()
            while (raw.endsWith(".") || raw.endsWith(",") || raw.endsWith(";") || raw.endsWith(")")) {
                raw = raw.dropLast(1)
            }
            if (raw.isNotEmpty()) {
                results.add(normalizeUrl(raw))
            }
        }

        // 2. localhost:PORT or 127.0.0.1:PORT without scheme
        val localHostRegex = "(?<!https?://)(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):\\d+(?:/[^\\s)\\]>\"']*)?".toRegex(RegexOption.IGNORE_CASE)
        for (match in localHostRegex.findAll(text)) {
            var raw = match.value.trim()
            while (raw.endsWith(".") || raw.endsWith(",") || raw.endsWith(";") || raw.endsWith(")")) {
                raw = raw.dropLast(1)
            }
            if (raw.isNotEmpty()) {
                results.add(normalizeUrl(raw))
            }
        }

        return results.toList()
    }

    /**
     * Determines whether a given URL is a local dev server / local preview URL.
     */
    fun isLocalWebUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("localhost") ||
                lower.contains("127.0.0.1") ||
                lower.contains("0.0.0.0") ||
                lower.contains("10.0.2.2")
    }

    /**
     * Checks if the code snippet is an HTML or Web page code block.
     */
    fun isHtmlOrWebCode(lang: String, code: String): Boolean {
        val l = lang.trim().lowercase()
        if (l in listOf("html", "htm", "xhtml", "svg", "web")) {
            return true
        }
        val lowerCode = code.lowercase()
        if (lowerCode.contains("<!doctype html") || (lowerCode.contains("<html") && lowerCode.contains("</html>"))) {
            return true
        }
        if (lowerCode.contains("<head>") && lowerCode.contains("<body>")) {
            return true
        }
        return false
    }
}
