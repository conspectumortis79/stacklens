package com.stacklens.adapter.out.health

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.util.closingAuto
import org.springframework.stereotype.Component
import java.sql.DriverManager

@Component
class DefaultDatabaseConnectionProbe(
    private val connectionPort: DatabaseScanConnection,
) : DatabaseConnectionProbePort {
    override fun probe(config: DatabaseConfig): ConnectionProbeResult =
        try {
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS)
            connectionPort.open(config).closingAuto { conn ->
                val reachable = conn.isValid(3)
                if (reachable) {
                    ConnectionProbeResult.Ok()
                } else {
                    ConnectionProbeResult.Failure("isValid(3) returned false")
                }
            }
        } catch (e: Exception) {
            ConnectionProbeResult.Failure(truncateMessage(e.message ?: e.javaClass.simpleName))
        } finally {
            DriverManager.setLoginTimeout(0)
        }
}

private const val LOGIN_TIMEOUT_SECONDS: Int = 5
