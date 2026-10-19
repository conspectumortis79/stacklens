package com.stacklens.desktop

import com.stacklens.StackLensApplication
import com.stacklens.domain.util.closingAuto
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Files
import java.nio.file.Path

internal class DesktopBackendBootstrap(
    private val startup: StartupRunner = DefaultStartupRunner,
    private val portProbe: (String) -> Int? = { url -> probePort(url) },
) : BackendLifecycle {
    @Volatile
    private var context: ConfigurableApplicationContext? = null

    override fun start(): Int {
        val dataDir = resolveDataDir().toAbsolutePath().toString()
        val h2Url = h2FileUrl(dataDir)

        return try {
            val appContext =
                startup.run(
                    StackLensApplication::class.java,
                    arrayOf(
                        "--server.address=127.0.0.1",
                        "--server.port=0",
                        "--spring.datasource.url=$h2Url",
                        "--stacklens.data-dir=$dataDir",
                    ),
                )
            context = appContext
            appContext.environment
                .getProperty("local.server.port")
                ?.toIntOrNull()
                ?: error("Embedded server port was not assigned")
        } catch (ex: Exception) {
            if (isDbLockException(ex)) {
                val port = findRunningServer(probe = portProbe)
                if (port != null) return port
                throw DuplicateInstanceException()
            }
            throw ex
        }
    }

    override fun stop() {
        context?.close()
        context = null
    }

    override fun checkpoint() {
        val ctx = context ?: return
        try {
            val ds = ctx.getBean(javax.sql.DataSource::class.java)
            ds.connection.closingAuto { conn ->
                conn.createStatement().closingAuto { stmt ->
                    stmt.execute("CHECKPOINT SYNC")
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun resolveDataDir(): Path {
        val dataDir = Path.of(System.getProperty("user.home"), ".stacklens", "data")
        Files.createDirectories(dataDir)
        return dataDir
    }

    companion object {
        internal fun resolveDataDir(home: String): Path {
            val dataDir = Path.of(home, ".stacklens", "data")
            Files.createDirectories(dataDir)
            return dataDir
        }

        internal fun h2FileUrl(dataDir: String): String = "jdbc:h2:file:$dataDir/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE"

        internal fun isDbLockException(ex: Throwable): Boolean {
            var cause: Throwable? = ex
            while (cause != null) {
                val msg = cause.message ?: ""
                if (msg.contains("90020") || msg.contains("Database may be already in use")) return true
                cause = cause.cause
            }
            return false
        }

        internal fun findRunningServer(
            fromPort: Int = 8090,
            toPort: Int = 8099,
            host: String = "127.0.0.1",
            probe: (String) -> Int? = ::probePort,
        ): Int? {
            for (port in fromPort..toPort) {
                val code = probe("http://$host:$port/")
                if (code != null && code in 200..499) return port
            }
            return null
        }

        internal fun probePort(url: String): Int? =
            try {
                val rawConn =
                    java.net
                        .URI(url)
                        .toURL()
                        .openConnection()
                if (rawConn !is java.net.HttpURLConnection) return null
                rawConn.connectTimeout = 800
                rawConn.readTimeout = 800
                rawConn.requestMethod = "GET"
                rawConn.instanceFollowRedirects = false
                val code = rawConn.responseCode
                rawConn.disconnect()
                code
            } catch (_: Exception) {
                null
            }
    }
}

fun interface StartupRunner {
    fun run(
        mainClass: Class<*>,
        args: Array<String>,
    ): ConfigurableApplicationContext
}

object DefaultStartupRunner : StartupRunner {
    override fun run(
        mainClass: Class<*>,
        args: Array<String>,
    ): ConfigurableApplicationContext =
        SpringApplicationBuilder(mainClass)
            .headless(false)
            .profiles("standalone")
            .run(*args)
}
