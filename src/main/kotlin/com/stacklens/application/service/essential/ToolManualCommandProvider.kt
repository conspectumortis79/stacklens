package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.Platform

interface ToolManualCommandProvider {
    val toolId: String

    fun manualCommand(platform: Platform): String
}
