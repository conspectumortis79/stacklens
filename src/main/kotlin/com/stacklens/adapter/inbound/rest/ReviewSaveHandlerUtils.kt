
package com.stacklens.adapter.inbound.rest

internal fun escapeHtml(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

internal fun escapeForJs(text: String): String =
    text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")
        .replace("\t", "\\t")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")

internal fun stripHtmlBlock(markdown: String): String = markdown.replace(Regex("""^```html\r?\n[\s\S]*?^```[ \t]*(\r?\n|$)""", setOf(RegexOption.MULTILINE)), "").trim()

internal fun extractHtmlBlock(markdown: String): String? {
    val m = Regex("""^```html\r?\n([\s\S]*?)^```[ \t]*(\r?\n|$)""", setOf(RegexOption.MULTILINE)).find(markdown)
    if (m != null) return m.groupValues[1]
    val trimmed = markdown.trim()
    if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
        return trimmed
    }
    return null
}

internal fun buildHtmlPage(
    title: String,
    markdown: String,
): String {
    val safeTitle = escapeHtml(title)
    val jsStr = escapeForJs(markdown)
    return buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"de\">")
        appendLine("<head>")
        appendLine("  <meta charset=\"UTF-8\">")
        appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("  <title>$safeTitle</title>")
        appendLine("  <style>")
        appendLine(
            "    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;max-width:960px;margin:40px auto;padding:0 24px;color:#1f1f1f;line-height:1.6}",
        )
        appendLine("    h1,h2,h3{border-bottom:1px solid #e0dbd4;padding-bottom:6px;margin-top:28px}")
        appendLine("    h1{font-size:1.7rem} h2{font-size:1.35rem} h3{font-size:1.1rem}")
        appendLine(
            "    table{border-collapse:collapse;width:100%;margin:16px 0} td,th{border:1px solid #ccc;padding:7px 11px} th{background:#f0ede8;font-weight:600}",
        )
        appendLine(
            "    pre{background:#f2f0eb;padding:14px 16px;border-radius:6px;overflow-x:auto;white-space:pre-wrap;word-break:break-word}",
        )
        appendLine("    code{background:#f0ede8;padding:1px 5px;border-radius:3px;font-size:.9em}")
        appendLine("    blockquote{border-left:3px solid #126a5a;margin-left:0;padding-left:16px;color:#555}")
        appendLine("    hr{border:none;border-top:1px solid #e0dbd4;margin:24px 0}")
        appendLine("    ul,ol{padding-left:24px}")
        appendLine("  </style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("  <div id=\"content\"></div>")
        appendLine("  <script src=\"https://cdn.jsdelivr.net/npm/marked@4/marked.min.js\"></script>")
        appendLine("  <script>")
        appendLine("    var md = \"$jsStr\";")
        appendLine("    var esc = function(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');};")
        appendLine(
            "    document.getElementById('content').innerHTML = (typeof marked !== 'undefined') ? marked.parse(md) : '<pre>' + esc(md) + '</pre>';",
        )
        appendLine("  </script>")
        appendLine("</body>")
        appendLine("</html>")
    }
}
