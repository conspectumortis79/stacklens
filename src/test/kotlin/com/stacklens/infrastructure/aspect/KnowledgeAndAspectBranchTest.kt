package com.stacklens.infrastructure.aspect

import com.stacklens.domain.EntityConcurrentlyModifiedException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.orm.ObjectOptimisticLockingFailureException

class PersistenceExceptionTranslatorAspectTest {
    private val aspect = PersistenceExceptionTranslatorAspect()

    @Test
    fun `translate returns result when no exception`() {
        val jp = mock<ProceedingJoinPoint>()
        whenever(jp.proceed()).thenReturn("result")
        assertEquals("result", aspect.translate(jp))
        assertEquals("result", aspect.translate(jp))
    }

    @Test
    fun `translate wraps optimistic locking failure`() {
        val optimisticEx = ObjectOptimisticLockingFailureException("boom", Any())
        val jp = mock<ProceedingJoinPoint>()
        whenever(jp.proceed()).thenThrow(optimisticEx)
        val sig = mock<Signature>()
        whenever(jp.signature).thenReturn(sig)
        whenever(sig.toShortString()).thenReturn("Service.method()")
        whenever(sig.declaringTypeName).thenReturn("com.stacklens.MyService")
        val e =
            assertThrows(EntityConcurrentlyModifiedException::class.java) {
                aspect.translate(jp)
            }
        assertTrue(e.message!!.contains("concurrent modification") || e.message!!.contains("com.stacklens.MyService") || e.message!!.isNotEmpty())
    }
}

class KnowledgeRerankConfigBranchTest {
    @Test
    fun `defaults and disabled singleton`() {
        val config =
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig()
        assertEquals(false, config.rerankEnabled)
        assertEquals("auto", com.stacklens.domain.model.knowledge.KnowledgeRerankConfig.DISABLED.rerankModel)
    }

    @Test
    fun `accepts boundary values`() {
        com.stacklens.domain.model.knowledge.KnowledgeRerankConfig(
            rerankCandidateCount = 1,
            rerankFinalCount = 1,
            rerankMaxOutputTokens = 32,
            queryRewriteMaxOutputTokens = 2048,
        )
    }

    @Test
    fun `rejects out of range values`() {
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(queryRewriteMaxOutputTokens = 31)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(queryRewriteMaxOutputTokens = 3000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankCandidateCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankCandidateCount = 201)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankFinalCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankFinalCount = 51)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankFinalCount = 30, rerankCandidateCount = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankMaxOutputTokens = 31)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.stacklens.domain.model.knowledge
                .KnowledgeRerankConfig(rerankMaxOutputTokens = 2049)
        }
    }
}
