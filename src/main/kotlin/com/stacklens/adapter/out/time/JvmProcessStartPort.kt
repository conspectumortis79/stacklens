package com.stacklens.adapter.out.time

import com.stacklens.application.port.out.time.ProcessStartPort
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object JvmProcessStartPort : ProcessStartPort {
    private val processStartedAt: Instant =
        Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime())

    override fun startedAt(): LocalDateTime = processStartedAt.atZone(ZoneId.systemDefault()).toLocalDateTime()
}
