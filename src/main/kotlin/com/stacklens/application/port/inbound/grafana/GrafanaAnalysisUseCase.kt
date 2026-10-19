package com.stacklens.application.port.inbound.grafana

import com.stacklens.application.port.out.grafana.GrafanaConfigInfo

interface GrafanaAnalysisReadUseCase {
    fun listConfigs(): List<GrafanaConfigInfo>

    fun listVisibleConfigs(): List<GrafanaConfigInfo>

    fun findConfig(id: Long): GrafanaConfigInfo?
}

interface GrafanaAnalysisWriteUseCase {
    fun generateDummyData(dashboardJson: String): Map<String, Any>

    fun rebuildDashboard(
        dashboardJson: String,
        instructions: String,
    ): Map<String, Any>
}

interface GrafanaAnalysisUseCase :
    GrafanaAnalysisReadUseCase,
    GrafanaAnalysisWriteUseCase
