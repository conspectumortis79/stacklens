package com.stacklens.domain.model.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class KnowledgeIndexGenerationTest {
    @Test
    fun `a new generation starts inactive with an unpersisted id`() {
        val created = LocalDateTime.of(2025, 9, 1, 12, 30)
        val generation =
            KnowledgeIndexGeneration(
                knowledgeBaseId = 42L,
                generationId = "gen-1",
                embeddingProfile = "ollama/nomic-embed-text:latest@768",
                createdAt = created,
            )

        assertEquals(0L, generation.id)
        assertEquals(42L, generation.knowledgeBaseId)
        assertEquals("gen-1", generation.generationId)
        assertEquals("ollama/nomic-embed-text:latest@768", generation.embeddingProfile)
        assertFalse(generation.active)
        assertEquals(created, generation.createdAt)
        assertNull(generation.activatedAt)
    }

    @Test
    fun `an activated generation keeps its mutable activation state`() {
        val activatedAt = LocalDateTime.of(2025, 9, 2, 8, 15)
        val generation =
            KnowledgeIndexGeneration(
                id = 7L,
                knowledgeBaseId = 1L,
                generationId = "gen-7",
                embeddingProfile = "profile",
                active = true,
                activatedAt = activatedAt,
            )

        generation.knowledgeBaseId = 9L
        generation.generationId = "gen-8"
        generation.embeddingProfile = "other-profile"
        generation.active = false
        generation.activatedAt = null

        assertEquals(7L, generation.id)
        assertEquals(9L, generation.knowledgeBaseId)
        assertEquals("gen-8", generation.generationId)
        assertEquals("other-profile", generation.embeddingProfile)
        assertFalse(generation.active)
        assertNull(generation.activatedAt)
    }

    @Test
    fun `createdAt defaults to the current time when it is not given`() {
        val before = LocalDateTime.now()
        val generation = KnowledgeIndexGeneration(knowledgeBaseId = 1L, generationId = "g", embeddingProfile = "p")

        assertTrue(generation.createdAt >= before)
    }
}
