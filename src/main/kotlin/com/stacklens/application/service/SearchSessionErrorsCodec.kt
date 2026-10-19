package com.stacklens.application.service

import com.stacklens.application.port.inbound.search.SearchSessionErrorsUseCase
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchSession

@ApplicationService
class SearchSessionErrorsCodec(
    private val jsonCodec: JsonCodecPort,
) : SearchSessionErrorsUseCase {
    override fun decodeErrors(session: SearchSession): List<DatabaseConnectionError> {
        val raw = session.databaseConnectionErrors?.takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            jsonCodec.decodeList(raw, DatabaseConnectionError::class)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeErrors(
        session: SearchSession,
        errors: List<DatabaseConnectionError>,
    ) {
        session.databaseConnectionErrors =
            if (errors.isEmpty()) {
                null
            } else {
                jsonCodec.encode(errors)
            }
    }
}
