package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.time.JvmProcessStartPort
import com.stacklens.adapter.out.time.SystemClockPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.time.ProcessStartPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TimeConfig {
    @Bean
    fun clockPort(): ClockPort = SystemClockPort

    @Bean
    fun processStartPort(): ProcessStartPort = JvmProcessStartPort
}
