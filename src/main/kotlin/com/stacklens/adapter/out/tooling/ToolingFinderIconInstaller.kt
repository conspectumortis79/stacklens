package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ProcessRunPort
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
class ToolingFinderIconInstaller(
    private val processRunPort: ProcessRunPort,
    private val platformPort: PlatformPort,
) {
    fun trySetMacOsFinderIcon(
        jarPath: String,
        iconPngBytes: ByteArray,
    ) {
        if (platformPort.detect().osType != OsType.MACOS) return
        val tempPng = File.createTempFile("stacklens-icon-", ".png")
        try {
            tempPng.writeBytes(iconPngBytes)
            val process =
                processRunPort.launch(listOf("fileicon", "set", jarPath, tempPng.absolutePath))
            process.waitFor(10, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            tempPng.delete()
        }
    }
}
