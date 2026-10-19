package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.process.ExecutablePathContributor
import com.stacklens.adapter.out.process.FileSystemExecutableProbe
import com.stacklens.adapter.out.process.HostExecutableLookup
import com.stacklens.application.port.out.platform.HostDirectoryPort
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ExecutableLookupPort
import com.stacklens.application.port.out.process.ShellPort
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ExecutableLookupConfig {
    @Bean
    fun fileSystemExecutableProbe(): FileSystemExecutableProbe = FileSystemExecutableProbe()

    @Bean
    fun executableLookupPort(
        shell: ShellPort,
        hostDirectories: HostDirectoryPort,
        platform: PlatformPort,
        contributors: ObjectProvider<ExecutablePathContributor>,
        executableProbe: FileSystemExecutableProbe,
    ): ExecutableLookupPort =
        HostExecutableLookup(
            shell = shell,
            hostDirectories = hostDirectories,
            platform = platform,
            contributors = contributors.orderedStream().toList(),
            executableProbe = executableProbe,
        )
}
