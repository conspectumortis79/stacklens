package com.stacklens.application.port.out.mcp

import java.io.File

fun interface McpScriptLocator {
    fun locateScript(): File?
}

fun interface McpNodeLocator {
    fun locateNode(): String?
}
