
package com.stacklens.application.service

internal object DrawioSupport {
    private val colorStyleKeys =
        setOf(
            "fillColor",
            "strokeColor",
            "fontColor",
            "gradientColor",
            "labelBackgroundColor",
            "labelBorderColor",
        )

    fun extract(text: String): String? {
        val blockRegex =
            Regex(
                """```(?:drawio|xml)[^\n]*\r?\n\s*(<mxfile[\s\S]*?</mxfile>)""",
                setOf(RegexOption.IGNORE_CASE),
            )
        val blockMatch = blockRegex.find(text)
        if (blockMatch != null) return blockMatch.groupValues[1].trim()
        val bareRegex = Regex("""(<mxfile[\s\S]*?</mxfile>)""", setOf(RegexOption.IGNORE_CASE))
        val mxfileMatch = bareRegex.find(text)
        if (mxfileMatch != null) return mxfileMatch.groupValues[1].trim()
        val mxModelRegex = Regex("""(<mxGraphModel[\s\S]*?</mxGraphModel>)""", setOf(RegexOption.IGNORE_CASE))
        return mxModelRegex
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.trim()
    }

    fun sanitize(xml: String): String {
        var result = xml.trim()
        val diagramInlineRegex =
            Regex(
                """<diagram[^>]*>\s*(<mxGraphModel[\s\S]*?</mxGraphModel>)\s*</diagram>""",
                setOf(RegexOption.IGNORE_CASE),
            )
        val inlineModel = diagramInlineRegex.find(result)?.groupValues?.get(1)
        if (inlineModel != null) result = inlineModel.trim()
        result =
            result.replace(
                Regex("""<Array\s+as="points"[^>]*>[\s\S]*?</Array>""", setOf(RegexOption.IGNORE_CASE)),
                "",
            )
        result = result.replace(Regex("""<mxPoint\b[^>]*/>""", setOf(RegexOption.IGNORE_CASE)), "")
        result = normalizeMxCellStyles(result)
        result =
            result.replace(Regex("""<mxGeometry\b([^>]*?)(/>|>)""")) { mr ->
                val attrs = mr.groupValues[1]
                val close = mr.groupValues[2]
                if ("as=" !in attrs) "<mxGeometry$attrs as=\"geometry\"$close" else mr.value
            }
        val seenIds = mutableSetOf<String>()
        var dupCounter = 0
        result =
            result.replace(Regex("""<mxCell\b([^>]*)>""")) { mr ->
                val cellId = Regex("""\bid="([^"]*)"""").find(mr.groupValues[1])?.groupValues?.get(1)
                if (cellId != null && !seenIds.add(cellId)) {
                    mr.value.replace("""id="$cellId"""", """id="sl-${++dupCounter}"""")
                } else {
                    mr.value
                }
            }
        if ("<root>" in result) {
            val hasCell0 = Regex("""<mxCell\b[^>]+\bid="0"""").containsMatchIn(result)
            val hasCell1 = Regex("""<mxCell\b[^>]+\bid="1"""").containsMatchIn(result)
            if (!hasCell0 || !hasCell1) {
                val prefix =
                    buildString {
                        if (!hasCell0) append("""<mxCell id="0"/>""")
                        if (!hasCell1) append("""<mxCell id="1" parent="0"/>""")
                    }
                result = result.replace("<root>", "<root>$prefix")
            }
        }
        result =
            result.replace(Regex("""<mxCell\b([^>]*)>""")) { mr ->
                val attrs = mr.groupValues[1]
                if (!attrs.contains("""parent="0""")) return@replace mr.value
                val cellId = Regex("""\bid="([^"]*)"""").find(attrs)?.let { it.groupValues[1] }
                when (cellId) {
                    "1" -> mr.value
                    "0" -> mr.value.replace(Regex("""\s*parent="0""""), "")
                    else -> mr.value.replace("""parent="0"""", """parent="1"""")
                }
            }
        result =
            result.replace(
                Regex("""(<mxCell\b[^>]+\bedge="1"[^>]*>[\s\S]*?)<mxGeometry\b([^>]*?)(/>|>)""", RegexOption.DOT_MATCHES_ALL),
            ) { mr ->
                val before = mr.groupValues[1]
                val gAttrs = mr.groupValues[2]
                val gClose = mr.groupValues[3]
                val fixedAttrs = if ("relative=" !in gAttrs) " relative=\"1\"$gAttrs" else gAttrs
                "$before<mxGeometry$fixedAttrs$gClose"
            }
        return result
    }

    private fun normalizeMxCellStyles(xml: String): String =
        xml.replace(Regex("""<mxCell\b([^>]*?)\sstyle="([^"]*)"([^>]*?)(/?>)""")) { mr ->
            val beforeStyle = mr.groupValues[1]
            val style = mr.groupValues[2]
            val afterStyle = mr.groupValues[3]
            val close = mr.groupValues[4]
            val normalizedStyle = normalizeStyleString(style)
            "<mxCell$beforeStyle style=\"$normalizedStyle\"$afterStyle$close"
        }

    private fun normalizeStyleString(style: String): String {
        val entries =
            style
                .split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { token ->
                    val idx = token.indexOf('=')
                    if (idx < 0) token to null else token.substring(0, idx) to token.substring(idx + 1)
                }.toMutableList()
        for (index in entries.indices) {
            val (key, value) = entries[index]
            if (value != null && key in colorStyleKeys) {
                entries[index] = key to normalizeColor(value)
            }
        }
        return entries.joinToString(";") { (key, value) -> if (value == null) key else "$key=$value" } + ";"
    }

    private fun normalizeColor(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("#")) return trimmed
        if (trimmed.equals("none", ignoreCase = true)) return trimmed
        if (trimmed.equals("inherit", ignoreCase = true)) return trimmed
        if (Regex("""[0-9a-fA-F]{3,8}""").matches(trimmed)) return "#$trimmed"
        return trimmed
    }
}
