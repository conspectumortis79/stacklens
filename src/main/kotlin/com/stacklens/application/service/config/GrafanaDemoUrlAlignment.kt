package com.stacklens.application.service.config

import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.platform.DemoGrafanaEndpointPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class GrafanaDemoUrlAlignment(
    private val grafanaConfigRepo: GrafanaConfigPort,
    private val aligner: DemoUrlAligner,
    private val grafanaEndpoint: DemoGrafanaEndpointPort,
) : DemoUrlAlignmentSection {
    override val label: String = "Grafana"

    override fun align(): List<String> =
        aligner.alignUrls(
            entries = grafanaConfigRepo.findAllGrafanaConfigs(),
            urlOf = { it.baseUrl },
            urlSetter = { config, url -> config.baseUrl = url },
            persist = { grafanaConfigRepo.saveGrafanaConfig(it) },
            alignWith = grafanaEndpoint::alignedUrl,
        )
}
