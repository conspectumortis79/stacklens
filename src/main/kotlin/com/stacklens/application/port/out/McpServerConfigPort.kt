package com.stacklens.application.port.out

import com.stacklens.domain.model.McpServerConfig

interface McpServerConfigPort {
    fun findAllMcpServerConfigs(): List<McpServerConfig>

    fun findMcpServerConfigById(id: Long): McpServerConfig?

    fun findFirstActiveMcpServerConfig(): McpServerConfig?

    fun saveMcpServerConfig(config: McpServerConfig): McpServerConfig

    fun deleteMcpServerConfigById(id: Long)

    fun deleteAllMcpServerConfigs()
}
