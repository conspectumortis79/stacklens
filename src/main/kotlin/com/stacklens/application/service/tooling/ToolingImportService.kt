package com.stacklens.application.service.tooling

import com.stacklens.application.port.inbound.tooling.ToolingImportUseCase
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.port.out.tooling.ToolJarRejectedException
import com.stacklens.application.port.out.tooling.ToolJarRejection
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool

@ApplicationService
class ToolingImportService(
    private val toolJarStore: ToolJarStore,
    private val toolRepo: ConfluenceToolPort,
    private val i18n: I18nPort,
    private val clock: ClockPort,
) : ToolingImportUseCase {
    override fun importTool(
        name: String,
        fileBytes: ByteArray,
        originalFilename: String,
    ): ConfluenceTool {
        val stored =
            try {
                toolJarStore.storeImported(originalFilename, fileBytes)
            } catch (e: ToolJarRejectedException) {
                throw IllegalArgumentException(
                    i18n.t(
                        when (e.rejection) {
                            ToolJarRejection.NOT_A_JAR -> "tooling.be_invalid_jar"
                            ToolJarRejection.MISSING_STACKLENS_MARKER -> "tooling.be_jar_not_stacklens_tool"
                        },
                    ),
                    e,
                )
            }
        val tool =
            ConfluenceTool(
                name = stored.embeddedName?.takeIf { it.isNotBlank() } ?: name.trim(),
                confluencePageUrl = "IMPORTED",
                status = AnalysisStatus.READY,
                jarPath = stored.jarPath,
                completedAt = clock.localDateTime(),
                icon = stored.embeddedIcon,
            )
        return toolRepo.saveConfluenceTool(tool)
    }
}
