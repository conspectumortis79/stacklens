package com.stacklens.application.service.review

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class PptxAnalysisLog(
    private val jsonCodec: JsonCodecPort,
) {
    fun encode(entries: List<String>): String = jsonCodec.encode(entries)

    fun decode(json: String): List<String> = jsonCodec.decodeList(json, String::class)
}
