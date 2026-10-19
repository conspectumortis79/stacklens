package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.TicketRefinement

interface SubtaskLedger {
    fun mergeAndPersist(
        entity: TicketRefinement,
        ticketKey: String,
        created: List<Map<String, Any?>>,
    )
}

@ApplicationService
class TicketRefinementSubtaskLedger(
    private val ticketRefinementRepo: TicketRefinementPort,
    private val jsonCodec: JsonCodecPort,
) : SubtaskLedger {
    override fun mergeAndPersist(
        entity: TicketRefinement,
        ticketKey: String,
        created: List<Map<String, Any?>>,
    ) {
        val ledger: MutableMap<String, List<String>> =
            runCatching {
                val raw = entity.subtasksCreatedJson
                val parsed: Map<String, Any?>? =
                    if (raw.isNullOrBlank()) {
                        null
                    } else {
                        jsonCodec.decodeMap(raw)
                    }
                if (parsed == null) {
                    mutableMapOf()
                } else {
                    parsed
                        .mapNotNull { (k, v) ->
                            val list = v as? List<*> ?: return@mapNotNull null
                            val strings = list.filterIsInstance<String>()
                            if (strings.size == list.size) k to strings else null
                        }.toMap()
                        .toMutableMap()
                }
            }.getOrDefault(mutableMapOf())
        val merged =
            (ledger[ticketKey].orEmpty() + created.mapNotNull { it["summary"] as? String }).distinct()
        ledger[ticketKey] = merged
        entity.subtasksCreatedJson = runCatching { jsonCodec.encode(ledger) }.getOrNull()
        ticketRefinementRepo.saveTicketRefinement(entity)
    }
}
