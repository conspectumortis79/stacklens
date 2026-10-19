package com.stacklens.application.service

import com.stacklens.application.port.out.IdGeneratorPort
import com.stacklens.application.port.out.sql.H2TestSessionPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class SqlTestRunner(
    private val h2TestSession: H2TestSessionPort,
    private val idGenerator: IdGeneratorPort,
) {
    private val log = LoggerFactory.getLogger(SqlTestRunner::class.java)

    data class TestRunResult(
        val columns: List<String>,
        val rows: List<List<String?>>,
        val rowCount: Int,
        val truncated: Boolean = false,
        val error: String? = null,
    )

    fun runTest(
        sql: String,
        testData: String,
    ): TestRunResult {
        val dbId = idGenerator.randomId().replace("-", "")
        return try {
            h2TestSession.open(dbId).use { session ->
                for (stmt in splitSetupSql(testData)) {
                    try {
                        session.executeStatement(stmt)
                    } catch (e: Exception) {
                        log.debug("[RunTest] Setup stmt skipped: {}", e.message)
                    }
                }
                val queryResult = session.runQuery(sql, maxRows = 200, timeoutSec = 10)
                TestRunResult(
                    columns = queryResult.columns,
                    rows = queryResult.rows,
                    rowCount = queryResult.rows.size,
                    truncated = queryResult.truncated,
                )
            }
        } catch (e: Exception) {
            log.warn("[RunTest] Query execution failed: {}", e.message)
            TestRunResult(columns = emptyList(), rows = emptyList(), rowCount = 0, error = e.message)
        }
    }

    fun splitSetupSql(script: String): List<String> {
        val statements = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in script.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("--") || trimmed.startsWith("#")) continue
            if (trimmed.isEmpty() && buf.isBlank()) continue
            buf.append(line).append('\n')
            if (trimmed.endsWith(";")) {
                val stmt =
                    buf
                        .toString()
                        .trim()
                        .trimEnd(';')
                        .trim()
                if (stmt.isNotBlank()) statements.add(stmt)
                buf.clear()
            }
        }
        val remaining =
            buf
                .toString()
                .trim()
                .trimEnd(';')
                .trim()
        if (remaining.isNotBlank()) statements.add(remaining)
        return statements
    }
}
