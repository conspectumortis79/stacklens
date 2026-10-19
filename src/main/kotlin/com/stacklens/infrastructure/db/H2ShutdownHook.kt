package com.stacklens.infrastructure.db

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.sql.DriverManager

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class H2ShutdownHook : ApplicationListener<ContextClosedEvent> {
    private val log = LoggerFactory.getLogger(H2ShutdownHook::class.java)

    @Volatile
    private var activeJdbcUrl: String? = null

    @Volatile
    private var activeUsername: String? = null

    @Volatile
    private var activePassword: String? = null

    @Volatile
    private var shutdownAlreadyRun: Boolean = false

    @Volatile
    internal var executor: H2ShutdownExecutor = DefaultH2ShutdownExecutor()

    @Volatile
    internal var runtime: Runtime = Runtime.getRuntime()

    @PostConstruct
    fun registerJvmHook() {
        captureConfig(envLookup = { System.getenv(it) })
        registerJvmHookInternal()
    }

    internal fun registerJvmHookInternal() {
        try {
            runtime.addShutdownHook(
                Thread({ runShutdown("JVM shutdown hook") }, "stacklens-h2-shutdown").apply {
                    isDaemon = false
                },
            )
            log.info("[H2ShutdownHook] registered JVM shutdown hook (url={})", activeJdbcUrl)
        } catch (e: IllegalStateException) {
            log.info(
                "[H2ShutdownHook] JVM already shutting down, skipping JVM-level hook registration (url={})",
                activeJdbcUrl,
            )
        }
    }

    internal fun captureConfig(envLookup: (String) -> String?) {
        activeJdbcUrl =
            envLookup("SPRING_DATASOURCE_URL")
                ?: System.getProperty("spring.datasource.url")
        activeUsername =
            envLookup("SPRING_DATASOURCE_USERNAME")
                ?: System.getProperty("spring.datasource.username")
                ?: "sa"
        activePassword =
            envLookup("SPRING_DATASOURCE_PASSWORD")
                ?: System.getProperty("spring.datasource.password")
                ?: ""
    }

    override fun onApplicationEvent(event: ContextClosedEvent) {
        runShutdown("Spring ContextClosedEvent")
    }

    @Synchronized
    fun runShutdown(trigger: String) {
        if (shutdownAlreadyRun) {
            log.debug("[H2ShutdownHook] shutdown already executed (trigger={}) — skipping", trigger)
            return
        }
        val url = activeJdbcUrl
        if (url == null || !isH2(url)) {
            log.info("[H2ShutdownHook] not an H2 datasource (url={}) — skipping SHUTDOWN", url)
            shutdownAlreadyRun = true
            return
        }
        log.info("[H2ShutdownHook] executing SHUTDOWN IMMEDIATELY (trigger={})", trigger)
        try {
            executor.shutdown(url, activeUsername, activePassword)
            log.info("[H2ShutdownHook] SHUTDOWN IMMEDIATELY executed successfully")
        } catch (e: Exception) {
            val alreadyClosed = isAlreadyClosedError(e)
            if (alreadyClosed) {
                log.debug("[H2ShutdownHook] database already closed — treating as success")
            } else {
                log.warn("[H2ShutdownHook] SHUTDOWN failed ({}): {}", e.javaClass.simpleName, e.message)
            }
        } finally {
            shutdownAlreadyRun = true
        }
    }

    internal fun isAlreadyClosedError(e: Throwable): Boolean =
        e.message?.contains("90013", ignoreCase = true) == true ||
            e.message?.contains("already closed", ignoreCase = true) == true

    private fun isH2(url: String): Boolean = url.startsWith("jdbc:h2:")
}

fun interface H2ShutdownExecutor {
    @Throws(Exception::class)
    fun shutdown(
        url: String,
        username: String?,
        password: String?,
    )
}

class DefaultH2ShutdownExecutor : H2ShutdownExecutor {
    override fun shutdown(
        url: String,
        username: String?,
        password: String?,
    ) {
        DriverManager.getConnection(url, username, password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("SHUTDOWN IMMEDIATELY")
            }
        }
    }
}
