package com.stacklens.application.service.tooling

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.tooling.ToolingMaintenanceUseCase
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.time.ProcessStartPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus

@ApplicationService
class ToolingStaleRunHealer(
    private val toolRepo: ConfluenceToolPort,
    private val i18n: I18nPort,
    private val startupHealer: StartupRecovery,
    private val processStart: ProcessStartPort,
    private val clock: ClockPort,
) : ToolingMaintenanceUseCase {
    override fun healStaleTools() {
        val ownedByThisProcess = processStart.startedAt()
        startupHealer.heal(
            logTag = "Tooling",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                toolRepo
                    .findAllByOrderByCreatedAtDesc()
                    .filter { it.createdAt.isBefore(ownedByThisProcess) && it.status in ORPHAN_STATUSES }
            },
            markFailed = {
                it.status = AnalysisStatus.CANCELLED
                it.errorMessage = i18n.t("tooling.be_aborted_restart")
                it.completedAt = clock.localDateTime()
            },
            save = { toolRepo.saveConfluenceTool(it) },
        )
    }

    private companion object {
        private val ORPHAN_STATUSES =
            setOf(
                AnalysisStatus.PENDING,
                AnalysisStatus.ANALYZING,
                AnalysisStatus.GENERATING,
                AnalysisStatus.BUILDING,
                AnalysisStatus.RUNNING,
            )
    }
}
