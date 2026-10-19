package com.stacklens.application.service.codeanalysis

import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeAnalysis

@ApplicationService
class CodeAnalysisCancellationService(
    private val analysisRepo: CodeAnalysisPort,
    private val i18n: I18nPort,
    private val clock: ClockPort,
) {
    fun cancelAnalysis(analysisId: Long) {
        val analysis = analysisRepo.findCodeAnalysisById(analysisId) ?: return
        if (analysis.status == AnalysisStatus.RUNNING || analysis.status == AnalysisStatus.PENDING) {
            markCancelled(analysis)
        }
    }

    fun cancelLatestRunningForRepo(repoConfigId: Long): Long? {
        val analysis =
            analysisRepo.findTopByRepoConfigIdOrderByCreatedAtDesc(repoConfigId)
                ?: return null
        if (analysis.status == AnalysisStatus.RUNNING || analysis.status == AnalysisStatus.PENDING) {
            markCancelled(analysis)
            return analysis.id
        }
        return null
    }

    private fun markCancelled(analysis: CodeAnalysis) {
        analysis.status = AnalysisStatus.CANCELLED
        analysis.errorMessage = i18n.t("code_analysis.be_cancelled")
        analysis.completedAt = clock.localDateTime()
        analysisRepo.saveCodeAnalysis(analysis)
    }
}
