package com.stacklens.application.port.inbound.search

import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchSession

interface SearchSessionErrorsUseCase {
    fun decodeErrors(session: SearchSession): List<DatabaseConnectionError>
}
