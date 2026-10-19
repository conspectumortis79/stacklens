package com.stacklens.application.service.grafana

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class DashboardPatchApplier(
    private val jsonCodec: JsonCodecPort,
) {
    @Suppress("UNCHECKED_CAST")
    fun applyPatch(
        originalJson: String,
        patchJson: String,
    ): String {
        val root = jsonCodec.decodeMap(originalJson).toMutableMap()
        val patch = jsonCodec.decodeMap(patchJson)

        val existingDashboard = root["dashboard"] as? Map<String, Any?>
        val dashboardNode: MutableMap<String, Any?> =
            if (existingDashboard != null) {
                existingDashboard.toMutableMap().also { root["dashboard"] = it }
            } else {
                root
            }

        val dashboardPatch = patch["dashboardPatch"] as? Map<String, Any?>
        if (dashboardPatch != null) {
            dashboardPatch.forEach { (key, value) ->
                if (key != "panels") dashboardNode[key] = value
            }
        }

        val panelsList: MutableList<Any?>? =
            (dashboardNode["panels"] as? List<Any?>)
                ?.toMutableList()
                ?.also { dashboardNode["panels"] = it }

        val modifiedPanels = patch["modifiedPanels"] as? List<Any?>
        if (modifiedPanels != null && panelsList != null) {
            for (modifiedPanel in modifiedPanels) {
                val panelId = (modifiedPanel as? Map<String, Any?>)?.get("id")?.idAsInt() ?: continue
                for (i in panelsList.indices) {
                    val current = panelsList[i] as? Map<String, Any?>
                    if (current?.get("id")?.idAsInt() == panelId) {
                        panelsList[i] = modifiedPanel
                        break
                    }
                }
            }
        }

        val removePanelIds = patch["removePanelIds"] as? List<Any?>
        if (removePanelIds != null && removePanelIds.isNotEmpty() && panelsList != null) {
            val idsToRemove = removePanelIds.mapNotNull { it?.idAsInt() }.toSet()
            val iter = panelsList.iterator()
            while (iter.hasNext()) {
                val cur = iter.next() as? Map<String, Any?>
                if (cur?.get("id")?.idAsInt() in idsToRemove) iter.remove()
            }
        }

        val addPanels = patch["addPanels"] as? List<Any?>
        if (addPanels != null && addPanels.isNotEmpty()) {
            val target =
                (dashboardNode["panels"] as? MutableList<Any?>)
                    ?: mutableListOf<Any?>().also { dashboardNode["panels"] = it }
            target.addAll(addPanels)
        }

        return jsonCodec.encode(root)
    }

    private fun Any?.idAsInt(): Int? =
        when (this) {
            is Number -> this.toInt()
            is String -> this.toIntOrNull()
            else -> null
        }
}
