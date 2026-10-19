package com.stacklens.adapter.out.time

import com.stacklens.application.port.out.time.ClockPort
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

object SystemClockPort : ClockPort {
    override fun now(): Instant = Instant.now()

    override fun epochMillis(): Long = now().toEpochMilli()

    override fun localDateTime(): LocalDateTime = LocalDateTime.now()

    override fun localDate(): LocalDate = LocalDate.now()
}
