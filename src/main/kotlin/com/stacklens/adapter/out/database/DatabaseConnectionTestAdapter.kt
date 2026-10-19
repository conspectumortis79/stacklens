package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.DatabaseConnectionTestPort
import com.stacklens.application.port.out.DatabaseConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.util.closingAuto
import org.springframework.stereotype.Component
import java.sql.SQLException

@Component
class DatabaseConnectionTestAdapter(
    private val connectionPort: DatabaseScanConnection,
    private val i18n: I18nPort,
) : DatabaseConnectionTestPort {
    override fun testConnection(config: DatabaseConfig): TestResult =
        try {
            connectionPort.open(config).closingAuto { conn ->
                val meta = conn.metaData
                TestResult.Success(
                    mapOf(
                        "success" to true,
                        "message" to i18n.t("config.be_db_conn_success", meta.databaseProductName, meta.databaseProductVersion),
                    ),
                )
            }
        } catch (e: SQLException) {
            TestResult.Failure(
                ConnectionFailureKind.OTHER,
                mapOf("success" to false, "message" to i18n.t("config.be_db_error", exceptionDisplayMessage(e))),
            )
        } catch (e: Exception) {
            TestResult.Failure(
                ConnectionFailureKind.CONNECTION,
                mapOf("success" to false, "message" to i18n.t("config.be_conn_error", exceptionDisplayMessage(e))),
            )
        }

    private fun exceptionDisplayMessage(e: Throwable): String = e.message ?: e.javaClass.simpleName
}
