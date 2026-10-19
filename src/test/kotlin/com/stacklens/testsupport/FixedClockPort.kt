package com.stacklens.testsupport

import com.stacklens.application.port.out.time.ClockPort
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class FixedClockPort(
    private var current: Instant,
) : ClockPort {
    constructor(epochMillis: Long) : this(Instant.ofEpochMilli(epochMillis))

    override fun now(): Instant = current

    override fun epochMillis(): Long = current.toEpochMilli()

    override fun localDateTime(): LocalDateTime = LocalDateTime.ofInstant(current, ZoneOffset.UTC)

    override fun localDate(): LocalDate = localDateTime().toLocalDate()

    fun advanceMillis(millis: Long) {
        current = current.plusMillis(millis)
    }

    fun setEpochMillis(epochMillis: Long) {
        current = Instant.ofEpochMilli(epochMillis)
    }
}
