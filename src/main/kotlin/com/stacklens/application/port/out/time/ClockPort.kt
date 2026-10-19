package com.stacklens.application.port.out.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

interface ClockPort {
    fun now(): Instant

    fun epochMillis(): Long

    fun localDateTime(): LocalDateTime

    fun localDate(): LocalDate
}
