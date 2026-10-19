package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.process.SleeperPort
import org.springframework.stereotype.Component

@Component
class ThreadSleepSleeper : SleeperPort {
    override fun sleep(millis: Long): Boolean =
        try {
            Thread.sleep(millis)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
}
