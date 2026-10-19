package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.async.BackgroundStartupTaskExecutor
import com.stacklens.application.port.out.async.StartupTaskExecutor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StartupTaskExecutorConfig {
    @Bean
    fun startupTaskExecutor(): StartupTaskExecutor = BackgroundStartupTaskExecutor()
}
