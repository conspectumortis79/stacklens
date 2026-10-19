package com.stacklens.application.service

import com.stacklens.application.port.out.ai.AiUsageContextPort

class FakeAiUsageContextPort : AiUsageContextPort {
    override fun setContext(
        kind: String,
        analysisId: Long,
    ) {
    }

    override fun clearContext() {
    }

    override fun <T> withContext(
        kind: String,
        analysisId: Long,
        block: () -> T,
    ): T = block()

    override fun addFilterSavings(tokens: Long) {
    }

    override fun setModelOverride(model: String) {
    }
}
