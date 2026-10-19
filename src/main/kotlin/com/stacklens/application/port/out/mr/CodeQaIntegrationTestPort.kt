package com.stacklens.application.port.out.mr

import com.stacklens.application.port.out.MrFetchPort

interface CodeQaIntegrationTestPort {
    fun getStreamingOutput(id: Long): String?

    fun runIntegrationTests(
        analysisId: Long,
        mrData: MrFetchPort.MrData,
    )
}
