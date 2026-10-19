package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.appconfig.ToolingConfigPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ToolingConfigAdapter(
    @Value("\${tooling.icon-png-timeout-seconds:5}")
    override val iconPngTimeoutSeconds: Long = 5,
) : ToolingConfigPort
