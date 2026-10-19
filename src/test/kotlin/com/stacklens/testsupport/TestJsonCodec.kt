package com.stacklens.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.JsonCodecPort

object TestJsonCodec {
    fun create(mapper: ObjectMapper = jacksonObjectMapper()): JsonCodecPort = JacksonJsonCodecAdapter(mapper)
}
