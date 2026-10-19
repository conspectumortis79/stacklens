package com.stacklens.application.port.out.sql

interface H2TestQueryResult {
    val columns: List<String>

    val rows: List<List<String?>>

    val truncated: Boolean
}

interface H2TestSession : AutoCloseable {
    fun executeStatement(sql: String): Boolean

    fun runQuery(
        sql: String,
        maxRows: Int = 200,
        timeoutSec: Int = 10,
    ): H2TestQueryResult

    override fun close()
}
