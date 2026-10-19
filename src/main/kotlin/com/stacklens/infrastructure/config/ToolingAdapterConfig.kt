package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.tooling.FileToolJarStore
import com.stacklens.adapter.out.tooling.ProcessToolJarRuntime
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.compiler.JavaRuntimePort
import com.stacklens.application.port.out.tooling.ToolJarRuntime
import com.stacklens.application.port.out.tooling.ToolJarStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ToolingAdapterConfig {
    @Bean
    fun toolJarStore(stackLensConfig: StackLensConfigPort): ToolJarStore = FileToolJarStore(stackLensConfig)

    @Bean
    fun toolJarRuntime(javaRuntime: JavaRuntimePort): ToolJarRuntime = ProcessToolJarRuntime(javaRuntime)
}
