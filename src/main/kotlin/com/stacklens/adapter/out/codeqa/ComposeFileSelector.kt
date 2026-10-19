package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.MrFetchPort
import java.io.File

class ComposeFileSelector(
    private val discovery: ComposeFileDiscovery,
    private val aiSelector: ComposeFileAiSelector,
) {
    fun findAndSelect(
        root: File,
        mrData: MrFetchPort.MrData,
        onEmit: (String) -> Unit,
    ): File? {
        val candidates = discovery.findComposeFiles(root)
        if (candidates.isEmpty()) return null
        candidates.forEach { onEmit("   ${it.relativeTo(root).path}\n") }
        val chosen =
            if (candidates.size == 1) {
                candidates.first()
            } else {
                aiSelector.select(candidates, mrData, root)
            }
        onEmit("✅ Compose: ${chosen.relativeTo(root).path}\n")
        return chosen
    }
}
