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

class PersistenceExceptionTranslatorAspectMessageCoverageTest {
    private val aspect = PersistenceExceptionTranslatorAspect()

    private fun joinPointFailingWith(error: Exception): ProceedingJoinPoint {
        val joinPoint = mock<ProceedingJoinPoint>()
        val signature = mock<Signature>()
        whenever(signature.declaringTypeName).thenReturn("com.stacklens.domain.model.Ticket")
        whenever(signature.toShortString()).thenReturn("Ticket.save()")
        whenever(joinPoint.signature).thenReturn(signature)
        whenever(joinPoint.proceed()).thenThrow(error)
        return joinPoint
    }

    @Test
    fun `an optimistic locking failure without a message is translated with a generated one`() {
        val joinPoint = joinPointFailingWith(ObjectOptimisticLockingFailureException(null as String?, null as Throwable?))

        val translated =
            assertThrows(EntityConcurrentlyModifiedException::class.java) {
                aspect.translate(joinPoint)
            }

        assertEquals("Concurrent modification in com.stacklens.domain.model.Ticket", translated.message)
    }

    @Test
    fun `an optimistic locking failure keeps the original message`() {
        val joinPoint = joinPointFailingWith(ObjectOptimisticLockingFailureException("row version stale", Any()))

        val translated =
            assertThrows(EntityConcurrentlyModifiedException::class.java) {
                aspect.translate(joinPoint)
            }

        assertTrue(translated.message!!.contains("row version stale"))
    }
}
