package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class GrafanaAnalyseController(
    private val grafanaAnalysisUseCase: GrafanaAnalysisUseCase,
) {
    @GetMapping("/grafana-analysis")
    fun grafanaAnalyse(model: Model): String {
        model.addAttribute(
            "grafanaConfigs",
            grafanaAnalysisUseCase.listVisibleConfigs(),
        )
        return "grafana-analysis"
    }
}
