package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.LoggingFixPartialUpdatePort
import com.stacklens.application.port.out.LoggingFixStageColumn
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class LoggingFixAnalysisPartialUpdater : LoggingFixPartialUpdatePort {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    override fun updateStageDiagnose(
        id: Long,
        value: String?,
    ): Int =
        entityManager
            .createQuery("UPDATE LoggingFixAnalysis e SET e.stageDiagnose = :value WHERE e.id = :id")
            .setParameter("value", value)
            .setParameter("id", id)
            .executeUpdate()

    @Transactional
    override fun updateStagePlan(
        id: Long,
        value: String?,
    ): Int =
        entityManager
            .createQuery("UPDATE LoggingFixAnalysis e SET e.stagePlan = :value WHERE e.id = :id")
            .setParameter("value", value)
            .setParameter("id", id)
            .executeUpdate()

    @Transactional
    override fun updateStageCode(
        id: Long,
        value: String?,
    ): Int =
        entityManager
            .createQuery("UPDATE LoggingFixAnalysis e SET e.stageCode = :value WHERE e.id = :id")
            .setParameter("value", value)
            .setParameter("id", id)
            .executeUpdate()

    @Transactional
    override fun updateStageTests(
        id: Long,
        value: String?,
    ): Int =
        entityManager
            .createQuery("UPDATE LoggingFixAnalysis e SET e.stageTests = :value WHERE e.id = :id")
            .setParameter("value", value)
            .setParameter("id", id)
            .executeUpdate()

    @Transactional
    override fun appendStageOutput(
        id: Long,
        column: LoggingFixStageColumn,
        chunk: String,
    ): Int {
        val propertyName =
            when (column) {
                LoggingFixStageColumn.DIAGNOSE -> "stageDiagnose"
                LoggingFixStageColumn.PLAN -> "stagePlan"
                LoggingFixStageColumn.CODE -> "stageCode"
                LoggingFixStageColumn.TESTS -> "stageTests"
            }
        val jpql =
            "UPDATE LoggingFixAnalysis e " +
                "SET e.$propertyName = COALESCE(e.$propertyName, '') || :chunk " +
                "WHERE e.id = :id"
        return entityManager
            .createQuery(jpql)
            .setParameter("chunk", chunk)
            .setParameter("id", id)
            .executeUpdate()
    }

    @Transactional
    override fun updateStatus(
        id: Long,
        status: String,
    ): Int =
        entityManager
            .createQuery("UPDATE LoggingFixAnalysis e SET e.status = :status WHERE e.id = :id")
            .setParameter("status", status)
            .setParameter("id", id)
            .executeUpdate()

    @Transactional
    override fun updateStatusWithErrorMessage(
        id: Long,
        status: String,
        errorMessage: String?,
        completedAt: LocalDateTime,
    ): Int =
        entityManager
            .createQuery(
                "UPDATE LoggingFixAnalysis e " +
                    "SET e.status = :status, e.errorMessage = :errorMessage, e.completedAt = :completedAt " +
                    "WHERE e.id = :id",
            ).setParameter("status", status)
            .setParameter("errorMessage", errorMessage)
            .setParameter("completedAt", completedAt)
            .setParameter("id", id)
            .executeUpdate()

    @Transactional
    override fun updateStatusAndCompletedAt(
        id: Long,
        status: String,
        completedAt: LocalDateTime,
    ): Int =
        entityManager
            .createQuery(
                "UPDATE LoggingFixAnalysis e " +
                    "SET e.status = :status, e.completedAt = :completedAt " +
                    "WHERE e.id = :id",
            ).setParameter("status", status)
            .setParameter("completedAt", completedAt)
            .setParameter("id", id)
            .executeUpdate()
}
