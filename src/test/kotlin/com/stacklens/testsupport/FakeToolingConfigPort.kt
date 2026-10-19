package com.stacklens.testsupport

import com.stacklens.application.port.out.appconfig.ToolingConfigPort

class FakeToolingConfigPort(
    override val iconPngTimeoutSeconds: Long = 5L,
) : ToolingConfigPort
