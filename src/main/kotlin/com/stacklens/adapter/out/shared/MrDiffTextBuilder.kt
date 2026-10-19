package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.DiffHunkParserPort

class MrDiffTextBuilder(
    private val diffHunkParser: DiffHunkParserPort,
    private val maxDiffChars: Int = DEFAULT_MAX_DIFF_CHARS,
    private val defaultPerFileCap: Int = DEFAULT_PER_FILE_CAP,
) {
    private companion object {
        const val DEFAULT_MAX_DIFF_CHARS = 80_000
        const val DEFAULT_PER_FILE_CAP = 8_000
    }

    fun build(fileDiffs: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        for ((filePath, diff) in fileDiffs) {
            if (diff.isBlank()) continue
            sb.append("### ").append(filePath).append('\n')
            sb.append("```diff\n")
            val body = formatDiffBody(filePath, diff)
            sb.append(body)
            if (!body.endsWith('\n')) sb.append('\n')
            sb.append("```\n\n")
            if (sb.length > maxDiffChars) {
                sb.append("…[Further files omitted – hunk headers preserved for the included files]…\n")
                break
            }
        }

        return sb.toString().take(maxDiffChars)
    }

    fun formatDiffBody(
        filePath: String,
        rawDiff: String,
        perFileCap: Int = defaultPerFileCap,
    ): String {
        if (rawDiff.length <= perFileCap) return rawDiff
        val hunks = diffHunkParser.hunks(rawDiff)
        if (hunks.isEmpty()) {
            return rawDiff.take(perFileCap) + "\n…[truncated – no hunk headers found]…"
        }
        val sb = StringBuilder()
        sb.append("[Diff body truncated to hunk headers only for file ").append(filePath).append(" — line numbers below are the authoritative source of truth]\n")
        for (h in hunks) {
            sb.append(h.header).append('\n')
        }
        return sb.toString()
    }
}
