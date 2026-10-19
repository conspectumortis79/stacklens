package com.stacklens.application.port.out.time

import java.time.LocalDateTime

interface ProcessStartPort {
    fun startedAt(): LocalDateTime
}
