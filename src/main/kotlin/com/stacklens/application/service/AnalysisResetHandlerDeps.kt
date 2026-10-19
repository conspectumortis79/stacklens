package com.stacklens.application.service

import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.JwtAnalysisPort
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.application.port.out.SprintReviewPort
import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.application.port.out.TicketRefinementPort

data class AnalysisResetHandlerDeps(
    val jiraTicketImplementationRepo: JiraTicketImplementationPort,
    val reviewAnalysisRepo: ReviewAnalysisPort,
    val sqlAnalysisRepo: SqlAnalysisPort,
    val jwtAnalysisRepo: JwtAnalysisPort,
    val loadTestAnalysisRepo: LoadTestAnalysisPort,
    val codeAnalysisRepo: CodeAnalysisPort,
    val securityAnalysisRepo: SecurityAnalysisPort,
    val sprintPreparationRepo: TicketRefinementPort,
    val sprintReviewRepo: SprintReviewPort,
    val codeQaAnalysisRepo: CodeQaAnalysisPort,
)
