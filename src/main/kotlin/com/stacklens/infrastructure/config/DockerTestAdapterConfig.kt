package com.stacklens.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.docker.DefaultDockerCliProber
import com.stacklens.adapter.out.docker.DockerCliAdapter
import com.stacklens.adapter.out.docker.DockerProcessEnvironment
import com.stacklens.adapter.out.dockertest.DockerOsHelper
import com.stacklens.adapter.out.grafana.DashboardMetricsParser
import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliProber
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.dockertest.DockerOsOperationsPort
import com.stacklens.application.port.out.grafana.GrafanaDashboardMetricsPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.application.port.out.time.ClockPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DockerTestAdapterConfig {
    @Bean
    fun dockerCliProber(executable: DockerExecutablePort): DockerCliProber = DefaultDockerCliProber(executable::resolveDockerExecutable)

    @Bean
    fun dockerCliPort(
        executable: DockerExecutablePort,
        environment: DockerProcessEnvironment,
    ): DockerCliPort = DockerCliAdapter(executable, environment::apply)

    @Bean(name = ["dockerTestEnvExecutor"], destroyMethod = "shutdown")
    fun dockerTestEnvExecutor(): java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "docker-test-env-reactor").apply { isDaemon = true }
        }

    @Bean(name = ["dockerTestStreamExecutor"], destroyMethod = "shutdown")
    fun dockerTestStreamExecutor(): java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newCachedThreadPool { r ->
            Thread(r, "docker-test-stream").apply { isDaemon = true }
        }

    @Bean
    fun dockerOsOperationsPort(
        dockerCommands: DockerCommandPort,
        executable: DockerExecutablePort,
        availability: DockerAvailabilityPort,
        sleeper: SleeperPort,
        clock: ClockPort,
    ): DockerOsOperationsPort = DockerOsHelper(dockerCommands, executable, availability, sleeper, clock)

    @Bean
    fun grafanaDashboardMetricsPort(objectMapper: ObjectMapper): GrafanaDashboardMetricsPort = DashboardMetricsParser(objectMapper)
}
