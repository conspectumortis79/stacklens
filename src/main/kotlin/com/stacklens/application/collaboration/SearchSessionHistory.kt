package com.stacklens.application.collaboration

import com.stacklens.domain.model.SearchSession

interface SearchSessionHistory {
    fun findSession(sessionId: Long): SearchSession?

    fun getSessionResultCount(sessionId: Long): Long
}
