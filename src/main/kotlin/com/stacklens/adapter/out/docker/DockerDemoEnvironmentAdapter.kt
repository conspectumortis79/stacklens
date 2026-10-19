package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.port.out.docker.DemoEnvironmentOutcome
import com.stacklens.application.port.out.docker.DemoEnvironmentPort
import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.process.SleeperPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
open class DockerDemoEnvironmentAdapter(
    private val composeFiles: ComposeFileLocationPort,
    private val dockerCli: DockerCliPort,
    private val dockerCommands: DockerCommandPort,
    private val dockerExecutable: DockerExecutablePort,
    private val sleeper: SleeperPort,
) : DemoEnvironmentPort {
    private val log = LoggerFactory.getLogger(DockerDemoEnvironmentAdapter::class.java)
    private val mysqlInit = MysqlInitScriptRunner(dockerCli, sleeper)

    override fun bringUp(timeoutSeconds: Long): DemoEnvironmentOutcome? {
        val composeFile =
            composeFiles.resolveComposeFile()
                ?: run {
                    log.warn("[DevEnvironmentToggle] no docker-compose.yml found, skipping up")
                    return null
                }
        val command =
            testdockerComposeCommand(
                dockerExecutable.resolveDockerExecutable(),
                composeFile,
                "--profile",
                "demo",
                "up",
                "-d",
                "--force-recreate",
                "--remove-orphans",
            )
        log.info("[DevEnvironmentToggle] docker compose up -d")
        val outcome = dockerCommands.runDockerCommand(command, composeFiles.containingDirectory(composeFile), timeoutSeconds)
        logOutcome("up -d", outcome.exitCode)
        return DemoEnvironmentOutcome(outcome.exitCode, outcome.output)
    }

    override fun bringDown() {
        val composeFile =
            composeFiles.resolveComposeFile()
                ?: run {
                    log.warn("[DevEnvironmentToggle] no docker-compose.yml found, skipping down")
                    return
                }
        runComposeDown(composeFile, testdockerProjectFlag(), "p=$TESTDOCKER_PROJECT_LABEL")
        runComposeDown(composeFile, emptyList(), "default-project")
        try {
            val names = composeFiles.findComposeContainerNames(composeFile)
            if (names.isNotEmpty()) {
                val args = listOf("stop") + names
                log.info("[DevEnvironmentToggle] docker stop {} (fallback by container_name)", names.joinToString(" "))
                logOutcome(
                    "stop by container_name",
                    dockerCli.run(args, composeFiles.containingDirectory(composeFile), LONG_RUN_TIMEOUT_SECONDS),
                )
            } else {
                log.info("[DevEnvironmentToggle] no container_name entries found in compose file, skipping stop fallback")
            }
        } catch (e: Exception) {
            log.warn("[DevEnvironmentToggle] stop fallback failed: {}", e.message)
        }
    }

    override fun ensureMysqlSchema() {
        val composeFile = composeFiles.resolveComposeFile() ?: return
        repairMysqlInitIfNeeded(composeFile)
    }

    internal fun repairMysqlInitIfNeeded(
        composeFile: String,
        probe: (String) -> Boolean = ::probeMysqlDemoSchema,
    ) {
        val initSql = composeFiles.resolveComposeSiblingFile(composeFile, MysqlDemoContainer.INIT_SQL_PATH)
        if (initSql == null) {
            log.warn(
                "[DevEnvironmentToggle] init SQL not found at {}, skipping MySQL repair",
                MysqlDemoContainer.INIT_SQL_PATH,
            )
            return
        }
        if (probe(composeFile)) {
            return
        }
        log.warn(
            "[DevEnvironmentToggle] MySQL demo schema '{}' not reachable — applying init SQL from {}",
            MysqlDemoContainer.DEMO_DATABASE,
            initSql,
        )
        val repaired = mysqlInit.applyAsRoot(initSql, composeFiles.containingDirectory(composeFile))
        if (probe(composeFile)) {
            return
        }
        log.warn(
            "[DevEnvironmentToggle] MySQL demo schema '{}' is still not reachable after applying init SQL from {} (init SQL applied: {})",
            MysqlDemoContainer.DEMO_DATABASE,
            initSql,
            repaired,
        )
    }

    internal fun probeMysqlDemoSchema(composeFile: String): Boolean {
        val args =
            listOf(
                "exec",
                MysqlDemoContainer.NAME,
                "mysql",
                "-u${MysqlDemoContainer.USER}",
                "-p${MysqlDemoContainer.USER_PASSWORD}",
                "-e",
                "SELECT 1",
                MysqlDemoContainer.DEMO_DATABASE,
            )
        repeat(MYSQL_PROBE_ATTEMPTS) { attempt ->
            val description = "mysql ${MysqlDemoContainer.USER} probe (attempt ${attempt + 1})"
            val outcome =
                try {
                    dockerCli.run(args, composeFiles.containingDirectory(composeFile), SHORT_RUN_TIMEOUT_SECONDS)
                } catch (e: Exception) {
                    log.warn("[DevEnvironmentToggle] {} failed: {}", description, e.message)
                    null
                }
            if (outcome != null) {
                logOutcome(description, outcome)
                if (outcome.successful) return true
            }
            sleeper.sleep(MYSQL_PROBE_DELAY_MS)
            if (Thread.currentThread().isInterrupted) return false
        }
        return false
    }

    private fun runComposeDown(
        composeFile: String,
        projectFlag: List<String>,
        label: String,
    ) {
        try {
            val args =
                listOf(
                    COMPOSE_SUBCOMMAND,
                    *projectFlag.toTypedArray(),
                    "-f",
                    composeFile,
                    "down",
                )
            log.info("[DevEnvironmentToggle] docker compose down ({})", label)
            logOutcome(
                "down ($label)",
                dockerCli.run(args, composeFiles.containingDirectory(composeFile), LONG_RUN_TIMEOUT_SECONDS),
            )
        } catch (e: Exception) {
            log.warn("[DevEnvironmentToggle] down step {} failed: {}", label, e.message)
        }
    }

    private fun logOutcome(
        description: String,
        exitCode: Int,
    ) {
        log.info("[DevEnvironmentToggle] {} exited with code {}", description, exitCode)
    }

    private fun logOutcome(
        description: String,
        result: DockerCliResult,
    ) {
        logOutcome(description, result.exitCode)
    }

    private companion object {
        const val TESTDOCKER_PROJECT_LABEL: String = "stacklens-testdocker"
        const val MYSQL_PROBE_ATTEMPTS: Int = 10
        const val MYSQL_PROBE_DELAY_MS: Long = 2_000
        const val SHORT_RUN_TIMEOUT_SECONDS: Long = 60L
        const val LONG_RUN_TIMEOUT_SECONDS: Long = 120L
    }
}
