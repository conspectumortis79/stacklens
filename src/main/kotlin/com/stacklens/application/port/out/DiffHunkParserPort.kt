package com.stacklens.application.port.out

import com.stacklens.domain.util.DiffHunk

interface DiffHunkParserPort {
    fun hunks(unifiedDiff: String): List<DiffHunk>

    fun lastNewLine(hunks: List<DiffHunk>): Int

    fun hunkContaining(
        hunks: List<DiffHunk>,
        newLine: Int,
    ): DiffHunk?

    fun summarise(
        unifiedDiff: String,
        perFileContextLines: Int = 2,
        maxBodyLines: Int = 6,
    ): String

    fun isPlausibleLine(
        hunks: List<DiffHunk>,
        newLine: Int,
    ): Boolean
}
