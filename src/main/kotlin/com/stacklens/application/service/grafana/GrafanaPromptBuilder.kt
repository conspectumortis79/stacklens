
package com.stacklens.application.service.grafana

import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class GrafanaPromptBuilder(
    private val promptLoaderPort: PromptReaderPort,
) {
    fun buildRebuildPatchPrompt(
        dashboardJson: String,
        instructions: String,
        lang: String,
    ): String {
        val cappedJson =
            if (dashboardJson.length > 20_000) {
                dashboardJson.take(20_000) + promptLoaderPort.get(lang, "common.jsonTruncated")
            } else {
                dashboardJson
            }
        return promptLoaderPort.get(lang, "grafana.rebuildPatch", mapOf("dashboardJson" to cappedJson, "instructions" to instructions))
    }

    fun buildDummyDataPrompt(
        dashboardJson: String,
        lang: String,
    ): String {
        val jsonSnippet =
            if (dashboardJson.length > 6000) {
                dashboardJson.take(6000) + promptLoaderPort.get(lang, "common.jsonTruncated")
            } else {
                dashboardJson
            }
        return promptLoaderPort.get(lang, "grafana.dummyData", mapOf("jsonSnippet" to jsonSnippet))
    }
}
