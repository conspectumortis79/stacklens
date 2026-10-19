package com.stacklens.application.service.config

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class ConfluenceDemoUrlAlignment(
    private val confluenceConfigRepo: ConfluenceConfigPort,
    private val aligner: DemoUrlAligner,
) : DemoUrlAlignmentSection {
    override val label: String = "Confluence"

    override fun align(): List<String> =
        aligner.alignUrls(
            entries = confluenceConfigRepo.findAllConfluenceConfigs(),
            urlOf = { it.baseUrl },
            urlSetter = { config, url -> config.baseUrl = url },
            persist = { confluenceConfigRepo.saveConfluenceConfig(it) },
        )
}
