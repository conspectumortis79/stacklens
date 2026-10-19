package com.stacklens.testsupport

import com.stacklens.application.port.out.appconfig.StackLensConfigPort

class FakeStackLensConfigPort(
    override val dataDir: String = "./data",
    override val dockerComposeUpTimeoutSeconds: Long = 120L,
    override val dockerComposeQueryTimeoutSeconds: Long = 5L,
) : StackLensConfigPort
