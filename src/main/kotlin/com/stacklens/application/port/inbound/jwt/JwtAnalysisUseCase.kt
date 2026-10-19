package com.stacklens.application.port.inbound.jwt

interface JwtAnalysisUseCase {
    fun analyze(token: String): Any
}
