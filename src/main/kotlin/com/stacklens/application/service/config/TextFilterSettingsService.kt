package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.TextFilterSettingsUseCase
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class TextFilterSettingsService(
    private val textFilterConfig: TextFilterConfigPort,
) : TextFilterSettingsUseCase {
    override fun isTextFilterEnabled(): Boolean = textFilterConfig.enabled

    override fun setTextFilterEnabled(enabled: Boolean): Boolean {
        textFilterConfig.setEnabled(enabled)
        return textFilterConfig.enabled
    }
}
