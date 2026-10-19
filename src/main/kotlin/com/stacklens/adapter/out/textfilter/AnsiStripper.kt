package com.stacklens.adapter.out.textfilter

internal object AnsiStripper {
    private val CSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val OSC = Regex("\u001B\\][^\u0007]*\u0007")
    private val CHARSET_ESC = Regex("\u001B[()][@-Z\\\\-_]")
    private val SINGLE_ESC = Regex("\u001B[=><~]")

    fun strip(input: String): String =
        input
            .replace(CSI, "")
            .replace(OSC, "")
            .replace(CHARSET_ESC, "")
            .replace(SINGLE_ESC, "")
            .replace("\r", "")
}
