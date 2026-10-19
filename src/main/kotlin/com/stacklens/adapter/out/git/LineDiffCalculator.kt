package com.stacklens.adapter.out.git

internal class LineDiffCalculator {
    companion object {
        private const val MAX_LINES = 5_000

        fun lcsDiff(
            a: List<String>,
            b: List<String>,
        ): List<GitDiffBuilder.DiffLine> {
            val left = if (a.size > MAX_LINES) a.take(MAX_LINES) else a
            val right = if (b.size > MAX_LINES) b.take(MAX_LINES) else b

            val n = left.size
            val m = right.size
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in 1..n) {
                for (j in 1..m) {
                    dp[i][j] =
                        if (left[i - 1] == right[j - 1]) {
                            dp[i - 1][j - 1] + 1
                        } else {
                            maxOf(dp[i - 1][j], dp[i][j - 1])
                        }
                }
            }
            val out = ArrayDeque<GitDiffBuilder.DiffLine>()
            var i = n
            var j = m
            while (i > 0 && j > 0) {
                if (left[i - 1] == right[j - 1]) {
                    out.addFirst(GitDiffBuilder.DiffLine(" ", left[i - 1]))
                    i--
                    j--
                } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                    out.addFirst(GitDiffBuilder.DiffLine("-", left[i - 1]))
                    i--
                } else {
                    out.addFirst(GitDiffBuilder.DiffLine("+", right[j - 1]))
                    j--
                }
            }
            while (i > 0) {
                out.addFirst(GitDiffBuilder.DiffLine("-", left[i - 1]))
                i--
            }
            while (j > 0) {
                out.addFirst(GitDiffBuilder.DiffLine("+", right[j - 1]))
                j--
            }
            if (a.size > MAX_LINES || b.size > MAX_LINES) {
                out.addLast(GitDiffBuilder.DiffLine(" ", "… (Datei für die Diff-Anzeige ab $MAX_LINES Zeilen abgeschnitten)"))
            }
            return out.toList()
        }

        fun collapseContext(
            lines: List<GitDiffBuilder.DiffLine>,
            context: Int = 3,
        ): List<GitDiffBuilder.DiffLine> {
            if (lines.isEmpty()) return lines
            val result = mutableListOf<GitDiffBuilder.DiffLine>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.sign != " ") {
                    result.add(line)
                    i++
                } else {
                    val runStart = i
                    while (i < lines.size && lines[i].sign == " ") i++
                    val runEnd = i
                    val runLen = runEnd - runStart

                    val keepStart = minOf(context, runLen)
                    val keepEnd = if (i < lines.size) minOf(context, runLen - keepStart) else 0
                    val skip = runLen - keepStart - keepEnd

                    for (k in runStart until runStart + keepStart) result.add(lines[k])
                    if (skip > 0) {
                        result.add(GitDiffBuilder.DiffLine("~", "@@ … $skip unchanged lines … @@"))
                    }
                    for (k in runEnd - keepEnd until runEnd) result.add(lines[k])
                }
            }
            return result
        }
    }
}
