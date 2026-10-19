package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.IdGeneratorPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UuidIdGenerator : IdGeneratorPort {
    override fun randomId(): String = UUID.randomUUID().toString()
}
