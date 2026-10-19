package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.AiUsage

interface TokenEstimator {
    fun estimateTokens(text: String): Long

    companion object {
        val Default: TokenEstimator =
            object : TokenEstimator {
                override fun estimateTokens(text: String): Long {
                    if (text.isEmpty()) return 0L
                    val raw = (text.length + AiUsage.CHARS_PER_TOKEN - 1) / AiUsage.CHARS_PER_TOKEN
                    return raw.toLong()
                }
            }
    }
}
