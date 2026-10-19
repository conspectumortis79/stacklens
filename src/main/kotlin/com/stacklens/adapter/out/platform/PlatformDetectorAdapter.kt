package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import org.springframework.stereotype.Component

@Component
class PlatformDetectorAdapter(
    private val detector: () -> Platform = { PlatformDetector.detect() },
) : PlatformPort {
    override fun detect(): Platform = detector()
}
