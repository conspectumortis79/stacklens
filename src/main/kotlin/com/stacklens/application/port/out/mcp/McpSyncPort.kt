package com.stacklens.application.port.out.mcp

import com.stacklens.domain.model.McpServerConfig

interface McpSyncPort {
    fun syncFromStacklens(): McpServerConfig
}
