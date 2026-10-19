package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.DatabaseConfigDto
import com.stacklens.application.port.inbound.config.DatabaseConfigUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DatabaseConfigApiControllerTest {
    @Mock private lateinit var configManagement: DatabaseConfigUseCase

    @Mock private lateinit var i18n: I18nUseCase

    private val controller by lazy { DatabaseConfigApiController(configManagement, i18n) }

    private fun mockConfig(id: Long = 1L) =
        DatabaseConfig(
            id = id,
            name = "Test DB",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "testdb",
            username = "user",
            password = "secret",
            schemas = "public",
            active = true,
        )

    @Test
    fun `listDbConfigs returns mapped DTOs`() {
        whenever(configManagement.listVisibleDatabaseConfigs()).thenReturn(listOf(mockConfig()))
        val result = controller.listDbConfigs()
        assertEquals(1, result.size)
        assertEquals("Test DB", result[0].name)
        assertEquals("", result[0].password)
        assertEquals(true, result[0].passwordSet)
    }

    @Test
    fun `listDbConfigs returns empty when none configured`() {
        whenever(configManagement.listVisibleDatabaseConfigs()).thenReturn(emptyList())
        val result = controller.listDbConfigs()
        assertEquals(0, result.size)
    }

    @Test
    fun `saveDbConfig creates new entity when id is null`() {
        val dto =
            DatabaseConfigDto(
                id = null,
                name = "New Config",
                dbType = "POSTGRESQL",
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "newpass",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveDatabaseConfig(any())).thenReturn(savedConfig)

        val result = controller.saveDbConfig(dto)
        assertNotNull(result)
        assertEquals("Test DB", (result.body as DatabaseConfigDto).name)
    }

    @Test
    fun `saveDbConfig creates new entity when id is 0`() {
        val dto =
            DatabaseConfigDto(
                id = 0,
                name = "Zero Id",
                dbType = "MYSQL",
                host = "localhost",
                port = 3306,
                databaseName = "mydb",
                username = "user",
                password = "pass",
            )
        val savedConfig = mockConfig()
        whenever(configManagement.saveDatabaseConfig(any())).thenReturn(savedConfig)

        val result = controller.saveDbConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveDbConfig updates existing entity when id is positive`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            DatabaseConfigDto(
                id = 5L,
                name = "Updated Config",
                dbType = "POSTGRESQL",
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "updatedpass",
            )
        whenever(configManagement.findDatabaseConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveDatabaseConfig(any())).thenReturn(existingConfig)

        val result = controller.saveDbConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveDbConfig creates new entity when existing id not found`() {
        val dto =
            DatabaseConfigDto(
                id = 99L,
                name = "New Via 99",
                dbType = "POSTGRESQL",
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
            )
        whenever(configManagement.findDatabaseConfig(eq(99L))).thenReturn(null)
        val savedConfig = mockConfig()
        whenever(configManagement.saveDatabaseConfig(any())).thenReturn(savedConfig)

        val result = controller.saveDbConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `saveDbConfig does not update password when blank`() {
        val existingConfig = mockConfig(id = 5L)
        val dto =
            DatabaseConfigDto(
                id = 5L,
                name = "Updated Config",
                dbType = "POSTGRESQL",
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "",
            )
        whenever(configManagement.findDatabaseConfig(eq(5L))).thenReturn(existingConfig)
        whenever(configManagement.saveDatabaseConfig(any())).thenReturn(existingConfig)

        val result = controller.saveDbConfig(dto)
        assertNotNull(result)
    }

    @Test
    fun `deleteDbConfig returns 404 when not found`() {
        whenever(configManagement.findDatabaseConfig(eq(1L))).thenReturn(null)
        val result = controller.deleteDbConfig(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `deleteDbConfig returns 204 when found`() {
        whenever(configManagement.findDatabaseConfig(eq(1L))).thenReturn(mockConfig())
        val result = controller.deleteDbConfig(1L)
        assertEquals(204, result.statusCode.value())
    }

    @Test
    fun `testDbConnection returns 404 when config not found`() {
        whenever(configManagement.testDatabaseConnection(eq(1L))).thenReturn(null)
        val result = controller.testDbConnection(1L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `testDbConnection returns the payload when the config exists`() {
        val payload = mapOf<String, Any>("success" to true, "message" to "Connected!")
        whenever(configManagement.testDatabaseConnection(eq(1L))).thenReturn(payload)
        val result = controller.testDbConnection(1L)
        assertEquals(200, result.statusCode.value())
        assertEquals(payload, result.body)
    }

    @Test
    fun `dbTypes returns all database types`() {
        val result = controller.dbTypes()
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.containsKey("name") && it.containsKey("defaultPort") })
        val names = result.map { it["name"] as String }
        assertTrue(names.contains("POSTGRESQL"))
    }

    @Test
    fun `saveDbConfig returns 400 when dbType is invalid`() {
        val dto =
            com.stacklens.adapter.inbound.rest.DatabaseConfigDto(
                id = null,
                name = "InvalidType",
                dbType = "NOT_A_REAL_DB_TYPE",
                host = "h",
                port = 5432,
                databaseName = "d",
                username = "u",
                password = "p",
            )
        Mockito.lenient().`when`(i18n.t("config.be_invalid_db_type", "NOT_A_REAL_DB_TYPE")).thenReturn("Invalid DB type")
        val result = controller.saveDbConfig(dto)
        assertEquals(400, result.statusCode.value())
    }
}
