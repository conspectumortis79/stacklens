package com.stacklens.desktop

import com.stacklens.StackLensApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import java.nio.file.Files
import java.nio.file.Path

internal data class HeadlessServerConfig(
    val dataDirAbs: String,
    val h2Url: String,
    val port: String,
    val address: String,
    val springArgs: List<String>,
)

internal fun computeHeadlessServerConfig(
    dataDirAbs: String,
    stacklensServerPort: String?,
    serverPort: String?,
    stacklensServerAddress: String?,
    defaultPort: Int = 8090,
): HeadlessServerConfig {
    val h2Url =
        "jdbc:h2:file:$dataDirAbs/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE"
    val port =
        (
            stacklensServerPort?.toIntOrNull()
                ?: serverPort?.toIntOrNull()
                ?: defaultPort
        ).toString()
    val address = stacklensServerAddress ?: "0.0.0.0"
    val springArgs =
        listOf(
            "--server.address=$address",
            "--server.port=$port",
            "--spring.datasource.url=$h2Url",
            "--stacklens.data-dir=$dataDirAbs",
        )
    return HeadlessServerConfig(
        dataDirAbs = dataDirAbs,
        h2Url = h2Url,
        port = port,
        address = address,
        springArgs = springArgs,
    )
}

fun interface SpringApplicationBuilderFactory {
    fun build(
        primarySource: Class<*>,
        args: Array<String>,
    ): SpringApplicationRunner
}

interface SpringApplicationRunner {
    fun headless(value: Boolean): SpringApplicationRunner

    fun profiles(vararg profiles: String): SpringApplicationRunner

    fun web(type: org.springframework.boot.WebApplicationType): SpringApplicationRunner

    fun registerShutdownHook(register: Boolean): SpringApplicationRunner

    fun run(): org.springframework.context.ConfigurableApplicationContext
}

internal class RealSpringApplicationBuilderFactory : SpringApplicationBuilderFactory {
    override fun build(
        primarySource: Class<*>,
        args: Array<String>,
    ): SpringApplicationRunner = RealSpringApplicationRunner(SpringApplicationBuilder(primarySource), args)

    internal class RealSpringApplicationRunner(
        private val builder: SpringApplicationBuilder,
        private val args: Array<String>,
    ) : SpringApplicationRunner {
        override fun headless(value: Boolean): SpringApplicationRunner = apply { builder.headless(value) }

        override fun profiles(vararg profiles: String): SpringApplicationRunner = apply { builder.profiles(*profiles) }

        override fun web(type: org.springframework.boot.WebApplicationType): SpringApplicationRunner = apply { builder.web(type) }

        override fun registerShutdownHook(register: Boolean): SpringApplicationRunner = apply { builder.registerShutdownHook(register) }

        override fun run(): org.springframework.context.ConfigurableApplicationContext = builder.run(*args)
    }
}

val defaultSpringApplicationBuilderFactory: SpringApplicationBuilderFactory = RealSpringApplicationBuilderFactory()

internal fun runHeadlessServer(
    springApplicationBuilderFactory: SpringApplicationBuilderFactory = defaultSpringApplicationBuilderFactory,
    systemPropertySetter: (String, String) -> Unit = { k, v -> System.setProperty(k, v) },
    directoryCreator: (Path) -> Unit = { dir ->
        Files
            .createDirectories(dir)
    },
    environmentProvider: (String) -> String? = { key -> System.getenv(key) },
    userHomeProvider: () -> String = { System.getProperty("user.home", "") },
) {
    systemPropertySetter("java.awt.headless", "true")
    systemPropertySetter("apple.awt.UIElement", "true")
    val dataDir =
        Path
            .of(userHomeProvider(), ".stacklens", "data")
    directoryCreator(dataDir)
    val dataDirAbs = dataDir.toAbsolutePath().toString()
    val config =
        computeHeadlessServerConfig(
            dataDirAbs = dataDirAbs,
            stacklensServerPort = environmentProvider("STACKLENS_SERVER_PORT"),
            serverPort = environmentProvider("SERVER_PORT"),
            stacklensServerAddress = environmentProvider("STACKLENS_SERVER_ADDRESS"),
        )

    val runner =
        springApplicationBuilderFactory.build(StackLensApplication::class.java, config.springArgs.toTypedArray())
    runner
        .headless(true)
        .profiles("standalone")
        .web(org.springframework.boot.WebApplicationType.SERVLET)
        .registerShutdownHook(true)
        .run()
}
