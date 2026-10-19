package com.stacklens.adapter.out.sql

import com.stacklens.application.port.out.sql.H2TestQueryResult
import com.stacklens.application.port.out.sql.H2TestSession
import com.stacklens.application.port.out.sql.H2TestSessionPort
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

@Component
class H2InMemoryTestSessionAdapter : H2TestSessionPort {
    override fun open(testId: String): H2TestSession {
        Class.forName("org.h2.Driver")
        return H2InMemoryTestSession(
            DriverManager.getConnection("jdbc:h2:mem:sqlrun_$testId;MODE=MySQL;DATABASE_TO_UPPER=false", "sa", ""),
        )
    }

    private class H2InMemoryTestSession(
        private val connection: Connection,
    ) : H2TestSession {
        override fun executeStatement(sql: String): Boolean = connection.createStatement().use { it.execute(sql) }

        override fun runQuery(
            sql: String,
            maxRows: Int,
            timeoutSec: Int,
        ): H2TestQueryResult {
            val columns = mutableListOf<String>()
            val rows = mutableListOf<List<String?>>()
            var truncated = false
            connection.createStatement().use { statement ->
                statement.queryTimeout = timeoutSec
                val rs = statement.executeQuery(sql)
                val meta = rs.metaData
                for (i in 1..meta.columnCount) {
                    columns.add(meta.getColumnLabel(i))
                }
                while (rs.next() && rows.size < maxRows) {
                    val row =
                        (1..meta.columnCount).map { i ->
                            val raw = rs.getObject(i)
                            raw?.toString()
                        }
                    rows.add(row)
                }
                if (rows.size >= maxRows && rs.next()) {
                    truncated = true
                }
            }
            return object : H2TestQueryResult {
                override val columns: List<String> = columns
                override val rows: List<List<String?>> = rows
                override val truncated: Boolean = truncated
            }
        }

        override fun close() {
            connection.close()
        }
    }
}
