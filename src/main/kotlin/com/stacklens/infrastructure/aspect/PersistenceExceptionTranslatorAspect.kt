package com.stacklens.infrastructure.aspect

import com.stacklens.domain.EntityConcurrentlyModifiedException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component

@Aspect
@Component
class PersistenceExceptionTranslatorAspect {
    private val log = LoggerFactory.getLogger(PersistenceExceptionTranslatorAspect::class.java)

    @Around(
        "execution(* com.stacklens.application.service..*.*(..)) && " +
            "target(com.stacklens.application.service..*)",
    )
    fun translate(joinPoint: ProceedingJoinPoint): Any? =
        try {
            joinPoint.proceed()
        } catch (e: ObjectOptimisticLockingFailureException) {
            log.debug(
                "Translating ObjectOptimisticLockingFailureException to EntityConcurrentlyModifiedException in {}",
                joinPoint.signature.toShortString(),
            )
            throw EntityConcurrentlyModifiedException(
                e.message ?: "Concurrent modification in ${joinPoint.signature.declaringTypeName}",
            )
        }
}
