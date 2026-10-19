package com.stacklens.application.service

import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ai.AiChatPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.application.port.out.grafana.GrafanaConfigLookupPort
import com.stacklens.application.port.out.platform.DemoConfigPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.grafana.DashboardPatchApplier
import com.stacklens.application.service.grafana.GrafanaPromptBuilder
import com.stacklens.application.service.grafana.JsonObjectExtractor
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.GrafanaConfig
import org.slf4j.LoggerFactory

@ApplicationService
class GrafanaAnalysisService(
    private val grafanaConfigPort: GrafanaConfigPort,
    private val copilotPort: AiChatPort,
    private val promptBuilder: GrafanaPromptBuilder,
    private val patchApplier: DashboardPatchApplier,
    private val jsonCodec: JsonCodecPort,
    private val aiUsageContext: AiUsageContextPort,
    private val clock: ClockPort,
    private val demoConfigFilter: DemoConfigPort,
) : GrafanaAnalysisUseCase,
    GrafanaConfigLookupPort {
    private val log = LoggerFactory.getLogger(GrafanaAnalysisService::class.java)

    override fun listConfigs(): List<GrafanaConfigInfo> = grafanaConfigPort.findAllGrafanaConfigs().map { it.toInfo() }

    override fun listVisibleConfigs(): List<GrafanaConfigInfo> = demoConfigFilter.apply(listConfigs()) { it.name }

    override fun findConfig(id: Long): GrafanaConfigInfo? = grafanaConfigPort.findGrafanaConfigById(id)?.toInfo()

    override fun generateDummyData(dashboardJson: String): Map<String, Any> {
        val lang = copilotPort.resolveLanguage()
        val prompt = promptBuilder.buildDummyDataPrompt(dashboardJson, lang)
        val model = copilotPort.resolveModel()
        val raw = copilotPort.execute(model, prompt)
        return parseDummyDataResponse(raw)
    }

    override fun rebuildDashboard(
        dashboardJson: String,
        instructions: String,
    ): Map<String, Any> {
        val lang = copilotPort.resolveLanguage()
        val prompt = promptBuilder.buildRebuildPatchPrompt(dashboardJson, instructions, lang)
        val model = copilotPort.resolveModel()
        val virtualId = clock.epochMillis()
        val raw =
            aiUsageContext.withContext("grafana", virtualId) {
                copilotPort.execute(model, prompt)
            }
        val result = parseAndApplyPatchResponse(dashboardJson, raw)
        return result + ("analysisId" to virtualId)
    }

    private fun parseAndApplyPatchResponse(
        originalJson: String,
        raw: String,
    ): Map<String, Any> {
        log.info(
            "[GrafanaRebuild] AI response received. Length: {} characters. Start: '{}'",
            raw.length,
            raw.take(500).replace('\n', '↵'),
        )

        val jsonStr = JsonObjectExtractor.extractFirstJsonObject(raw)
        if (jsonStr != null) {
            try {
                val parsed = jsonCodec.decodeMap(jsonStr)
                val summary = (parsed["summary"] as? String).orEmpty()
                val changes =
                    (parsed["changes"] as? List<*>)
                        ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
                        ?: emptyList()

                val patchNode = parsed["patch"]
                if (patchNode is Map<*, *>) {
                    val patchedJson =
                        try {
                            patchApplier.applyPatch(originalJson, jsonCodec.encode(patchNode))
                        } catch (e: Exception) {
                            log.warn("[GrafanaRebuild] Patch application failed: {}", e.message)
                            originalJson
                        }
                    log.info("[GrafanaRebuild] ✓ Format 'patch' detected. {} changes.", changes.size)
                    return mapOf(
                        "summary" to summary,
                        "changes" to changes,
                        "dashboardJson" to patchedJson,
                        "parseSuccess" to true,
                    )
                }

                val resultNode = parsed["result"]
                if (resultNode is Map<*, *>) {
                    val dashboardJson = jsonCodec.encode(resultNode)
                    val panelCount =
                        (resultNode["panels"] as? List<*>)?.size
                            ?: ((resultNode["dashboard"] as? Map<*, *>)?.get("panels") as? List<*>)?.size
                    log.info(
                        "[GrafanaRebuild] ✓ Format 'result' detected (fallback). Panels: {}. {} changes.",
                        panelCount ?: "?",
                        changes.size,
                    )
                    return mapOf(
                        "summary" to summary,
                        "changes" to changes,
                        "dashboardJson" to dashboardJson,
                        "parseSuccess" to true,
                    )
                }

                if (parsed.containsKey("panels") || (parsed["dashboard"] is Map<*, *>)) {
                    log.info("[GrafanaRebuild] ✓ AI returned direct Grafana dashboard JSON.")
                    return mapOf(
                        "summary" to "",
                        "changes" to emptyList<String>(),
                        "dashboardJson" to jsonStr,
                        "parseSuccess" to true,
                    )
                }

                log.warn(
                    "[GrafanaRebuild] ⚠ JSON found but no known format (no 'result', 'patch' or 'panels'). JSON keys: {}",
                    parsed.keys,
                )
            } catch (e: Exception) {
                log.warn("[GrafanaRebuild] ⚠ JSON parsing failed: {}. Raw: '{}'", e.message, raw.take(300))
            }
        } else {
            log.warn(
                "[GrafanaRebuild] ⚠ No JSON found in AI response. Response start: '{}'",
                raw.take(400).replace('\n', '↵'),
            )
        }

        log.warn("[GrafanaRebuild] ⚠ Could not process AI response. Returning original JSON.")
        return mapOf(
            "summary" to "",
            "changes" to emptyList<String>(),
            "dashboardJson" to originalJson,
            "parseSuccess" to false,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDummyDataResponse(raw: String): Map<String, Any> {
        val json = JsonObjectExtractor.extractFirstJsonObject(raw) ?: return mapOf("panels" to emptyList<Any>())
        return try {
            jsonCodec.decodeMap(json) as Map<String, Any>
        } catch (_: Exception) {
            mapOf("panels" to emptyList<Any>())
        }
    }

    private fun GrafanaConfig.toInfo() =
        GrafanaConfigInfo(
            id = id,
            name = name,
            baseUrl = baseUrl,
            username = username,
            password = password,
            apiToken = apiToken,
            orgId = orgId,
        )
}
