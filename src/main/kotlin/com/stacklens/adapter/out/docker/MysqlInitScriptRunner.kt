package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.application.port.out.process.SleeperPort
import org.slf4j.LoggerFactory

internal class MysqlInitScriptRunner(
    private val dockerCli: DockerCliPort,
    private val sleeper: SleeperPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun applyAsRoot(
        initSql: String,
        composeDir: String?,
    ): Boolean {
        if (!awaitSettledServer(initSql, composeDir)) {
            return false
        }
        return MysqlDemoContainer.rootLogins().any { applyWith(it, initSql, composeDir) }
    }

    private fun awaitSettledServer(
        initSql: String,
        composeDir: String?,
    ): Boolean {
        repeat(SETTLE_ATTEMPTS) {
            val probe = pid1Probe(composeDir) ?: return false
            if (probe.successful) {
                return true
            }
            if (probe.timedOut || !sleeper.sleep(SETTLE_DELAY_MS)) {
                return false
            }
        }
        log.warn(
            "[MysqlInitScriptRunner] '{}' is still inside its entrypoint initialisation after {} ms – skipping init SQL '{}' instead of racing the official init",
            MysqlDemoContainer.NAME,
            SETTLE_ATTEMPTS * SETTLE_DELAY_MS,
            initSql,
        )
        return false
    }

    private fun pid1Probe(composeDir: String?): DockerCliResult? =
        try {
            dockerCli.run(
                listOf("exec", MysqlDemoContainer.NAME, "sh", "-c", PID1_CHECK),
                composeDir,
                PID1_TIMEOUT_SECONDS,
            )
        } catch (e: Exception) {
            log.warn("[MysqlInitScriptRunner] PID 1 probe for '{}' failed: {}", MysqlDemoContainer.NAME, e.message)
            null
        }

    private fun applyWith(
        login: MysqlRootLogin,
        initSql: String,
        composeDir: String?,
    ): Boolean {
        val args = listOfNotNull("exec", "-i", MysqlDemoContainer.NAME, "mysql", "-uroot", login.cliOption())
        val result =
            try {
                dockerCli.run(args, composeDir, INIT_SQL_TIMEOUT_SECONDS, initSql)
            } catch (e: Exception) {
                log.warn(
                    "[MysqlInitScriptRunner] applying '{}' with {} threw: {}",
                    initSql,
                    login.logLabel(),
                    e.message,
                )
                return false
            }
        if (result.successful) {
            return true
        }
        log.warn(
            "[MysqlInitScriptRunner] rejected '{}' with {} (exit code {}{})",
            initSql,
            login.logLabel(),
            result.exitCode,
            if (result.timedOut) ", timed out" else "",
        )
        return false
    }

    private companion object {
        const val PID1_CHECK = "grep -qx mysqld /proc/1/comm"
        const val SETTLE_ATTEMPTS = 15
        const val SETTLE_DELAY_MS = 2_000L
        const val PID1_TIMEOUT_SECONDS = 30L
        const val INIT_SQL_TIMEOUT_SECONDS = 120L
    }
}
