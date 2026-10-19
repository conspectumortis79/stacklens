package com.stacklens.application.service.config
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.DatabaseConnectionTestPort
import com.stacklens.application.port.out.DatabaseConnectionTestPort.TestResult
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DatabaseConfigServiceTest {
    private val dbConfigRepo: DatabaseConfigPort = mock()

    private val connectionTest: DatabaseConnectionTestPort = mock()

    private val service = DatabaseConfigService(dbConfigRepo, connectionTest, PassthroughDemoConfigFilter.instance)

    private val database =
        com.stacklens.domain.model.DatabaseConfig(
            name = "db",
            dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
            host = "h",
            port = 5432,
            databaseName = "d",
            username = "u",
            password = "p",
        )

    @Test
    fun `listDatabaseConfigs returns every stored config`() {
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(listOf(database))
        assertEquals(listOf(database), service.listDatabaseConfigs())
    }

    @Test
    fun `findDatabaseConfig returns the config of the requested id`() {
        whenever(dbConfigRepo.findDatabaseConfigById(1L)).thenReturn(database)
        assertEquals(database, service.findDatabaseConfig(1L))
    }

    @Test
    fun `saveDatabaseConfig stores and returns the config`() {
        whenever(dbConfigRepo.saveDatabaseConfig(database)).thenReturn(database)
        assertEquals(database, service.saveDatabaseConfig(database))
    }

    @Test
    fun `deleteDatabaseConfig removes the config of the requested id`() {
        service.deleteDatabaseConfig(1L)
        verify(dbConfigRepo).deleteDatabaseConfigById(1L)
    }

    @Test
    fun `deleteAllDatabaseConfigs empties the store`() {
        service.deleteAllDatabaseConfigs()
        verify(dbConfigRepo).deleteAllDatabaseConfigs()
    }

    @Test
    fun `testDatabaseConnection returns the payload when the config exists`() {
        val payload = mapOf<String, Any>("success" to true, "message" to "ok")
        whenever(dbConfigRepo.findDatabaseConfigById(1L)).thenReturn(database)
        whenever(connectionTest.testConnection(database)).thenReturn(TestResult.Success(payload))

        assertEquals(payload, service.testDatabaseConnection(1L))
    }

    @Test
    fun `testDatabaseConnection returns null when the config is missing`() {
        whenever(dbConfigRepo.findDatabaseConfigById(1L)).thenReturn(null)

        assertEquals(null, service.testDatabaseConnection(1L))
        verify(connectionTest, never()).testConnection(any())
    }
}
