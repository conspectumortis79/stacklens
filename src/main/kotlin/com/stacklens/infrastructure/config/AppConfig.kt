package com.stacklens.infrastructure.config

import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.grafana.GrafanaHttpClient
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.SleeperPort
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import kotlin.reflect.full.memberProperties

@Configuration
class WebClientConfig {
    private val log = LoggerFactory.getLogger(WebClientConfig::class.java)

    @Value("\${webclient.connect-timeout:30000}")
    private val connectTimeoutMs: Int = 30_000

    @Value("\${webclient.read-timeout:180000}")
    private val readTimeoutMs: Int = 180_000

    @Value("\${webclient.pool.max-connections:50}")
    private val maxConnections: Int = 50

    @Value("\${webclient.pool.max-idle-time-seconds:30}")
    private val maxIdleTimeSeconds: Int = 30

    @Bean
    fun connectionProvider(): ConnectionProvider =
        ConnectionProvider
            .builder("stacklens-shared")
            .maxConnections(maxConnections)
            .maxIdleTime(Duration.ofSeconds(maxIdleTimeSeconds.toLong()))
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .build()

    @Bean
    fun webClientBuilder(): WebClient.Builder {
        val httpClient =
            HttpClient
                .create(connectionProvider())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(::configureConnection)
                .responseTimeout(Duration.ofMillis(readTimeoutMs.toLong()))
        val strategies =
            ExchangeStrategies
                .builder()
                .codecs(::configureCodecs)
                .build()
        return WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
    }

    private fun configureCodecs(configurer: org.springframework.http.codec.ClientCodecConfigurer) {
        configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)
    }

    private fun configureConnection(conn: Connection) {
        conn.addHandlerLast(ReadTimeoutHandler(readTimeoutMs / 1000))
    }

    @Bean
    fun restTemplate() = RestTemplate()

    @Bean
    fun grafanaHttpClient(
        i18n: I18nPort,
        sleeper: SleeperPort,
    ): GrafanaHttpClient = GrafanaHttpClient(i18n = i18n, sleeper = sleeper)
}

@Configuration
class AsyncConfig {
    private val log = LoggerFactory.getLogger(AsyncConfig::class.java)

    private fun saturationHandler(name: String) =
        ThreadPoolExecutor.CallerRunsPolicy().let { fallback ->
            java.util.concurrent.RejectedExecutionHandler { task, exec ->
                log.warn(
                    "{} executor saturated — running on caller thread (active={}, queue={}, completed={})",
                    name,
                    exec.activeCount,
                    exec.queue.size,
                    exec.completedTaskCount,
                )
                fallback.rejectedExecution(task, exec)
            }
        }

    @Value("\${async.search.core-pool-size:8}")
    private val searchCorePoolSize: Int = 8

    @Value("\${async.search.max-pool-size:16}")
    private val searchMaxPoolSize: Int = 16

    @Bean(name = ["searchTaskExecutor"])
    fun searchTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = searchCorePoolSize
        executor.maxPoolSize = searchMaxPoolSize
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("search-")
        executor.setRejectedExecutionHandler(saturationHandler("search"))
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }

    @Value("\${async.analysis.core-pool-size:4}")
    private val analysisCorePoolSize: Int = 4

    @Value("\${async.analysis.max-pool-size:8}")
    private val analysisMaxPoolSize: Int = 8

    @Bean(name = ["analysisTaskExecutor"])
    fun analysisTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = analysisCorePoolSize
        executor.maxPoolSize = analysisMaxPoolSize
        executor.setQueueCapacity(50)
        executor.setThreadNamePrefix("analysis-")
        executor.setRejectedExecutionHandler(saturationHandler("analysis"))
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }

    @Value("\${async.sprint-review.pool-size:4}")
    private val sprintReviewPoolSize: Int = 4

    @Bean(name = ["sprintReviewTaskExecutor"])
    fun sprintReviewTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = sprintReviewPoolSize
        executor.maxPoolSize = sprintReviewPoolSize
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("sprint-review-ai-")
        executor.setDaemon(true)
        executor.setRejectedExecutionHandler(saturationHandler("sprint-review"))
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }

    @Bean(name = ["pipelineTaskExecutor"])
    fun pipelineTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 8
        executor.setQueueCapacity(64)
        executor.setThreadNamePrefix("copilot-pipeline")
        executor.setDaemon(true)
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(false)
        executor.initialize()
        return executor
    }
}

@Configuration
class JacksonKotlinConfig {
    @Bean
    fun kotlinModule(): KotlinModule =
        KotlinModule
            .Builder()
            .configure(KotlinFeature.StrictNullChecks, false)
            .build()
}

@Component
open class KotlinReflectionWarmup : ApplicationRunner {
    open fun probe() {
        @Suppress("UnusedVariable")
        val unused = emptyList<Any>()::class.memberProperties
    }

    override fun run(args: ApplicationArguments) {
        try {
            probe()
        } catch (_: Exception) {
        }
    }
}
