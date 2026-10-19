package com.stacklens.adapter.out.database

import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseScanAdapterExtractTest {
    private val adapter = DatabaseScanAdapter(DriverManagerDatabaseScanConnectionPort(DefaultJdbcUrlProvider()))

    @Test
    fun `extractCrossRefTerms returns table and column names`() {
        val result =
            SearchResult(
                sessionId = 1,
                keyword = "",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                tableName = "users",
                columnName = "email",
            )
        val terms = adapter.extractCrossRefTerms(result)
        assertEquals(listOf("users", "email"), terms)
    }

    @Test
    fun `extractCrossRefTerms only includes column when resultType is COLUMN or INDEX`() {
        val columnNotColType =
            SearchResult(
                sessionId = 1,
                keyword = "",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.TABLE,
                tableName = "users",
                columnName = "email",
            )
        val terms = adapter.extractCrossRefTerms(columnNotColType)
        assertEquals(listOf("users"), terms)
    }

    @Test
    fun `extractCrossRefTerms includes column when resultType is INDEX`() {
        val indexResult =
            SearchResult(
                sessionId = 1,
                keyword = "",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.INDEX,
                tableName = "users",
                columnName = "idx_email",
            )
        val terms = adapter.extractCrossRefTerms(indexResult)
        assertEquals(listOf("users", "idx_email"), terms)
    }

    @Test
    fun `extractCrossRefTerms filters out terms shorter than 3 chars`() {
        val result =
            SearchResult(
                sessionId = 1,
                keyword = "",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                tableName = "ab",
                columnName = "cd",
            )
        val terms = adapter.extractCrossRefTerms(result)
        assertTrue(terms.isEmpty())
    }

    @Test
    fun `extractCrossRefTerms filters by keyword when keyword set`() {
        val result =
            SearchResult(
                sessionId = 1,
                keyword = "user",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                tableName = "users",
                columnName = "user_email",
            )
        val terms = adapter.extractCrossRefTerms(result)
        assertEquals(2, terms.size)
        assertTrue(terms.all { it.contains("user", ignoreCase = true) })
    }

    @Test
    fun `extractCrossRefTerms returns empty when keyword set but no terms match in any term`() {
        val result =
            SearchResult(
                sessionId = 1,
                keyword = "nomatch",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                tableName = "users",
                columnName = "email",
            )
        val terms = adapter.extractCrossRefTerms(result)
        assertTrue(terms.isEmpty())
    }

    @Test
    fun `extractCrossRefTerms returns empty when no tableName and not column-type or index-type`() {
        val result =
            SearchResult(
                sessionId = 1,
                keyword = "",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.TABLE,
            )
        val terms = adapter.extractCrossRefTerms(result)
        assertTrue(terms.isEmpty())
    }

    @Test
    fun `extractCrossRefTerms deduplicates terms`() {
        val result =
            SearchResult(
                sessionId = 1,
                keyword = "",
                sourceType = SourceType.DATABASE,
                resultType = ResultType.COLUMN,
                tableName = "users",
                columnName = "users",
            )
        val terms = adapter.extractCrossRefTerms(result)
        assertEquals(listOf("users"), terms)
    }
}
