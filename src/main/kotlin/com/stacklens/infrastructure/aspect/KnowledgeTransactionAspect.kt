package com.stacklens.infrastructure.aspect

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Aspect
@Component
class KnowledgeTransactionAspect(
    transactionManager: PlatformTransactionManager,
) {
    private val template = TransactionTemplate(transactionManager)

    @Around("@annotation(com.stacklens.application.stereotype.ApplicationTransactional)")
    fun inTransaction(joinPoint: ProceedingJoinPoint): Any? = template.execute { joinPoint.proceed() }
}
