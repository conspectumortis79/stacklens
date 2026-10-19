package com.stacklens.application.service.pipeline

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.pipeline.StageOutputPort
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.IvanStageRunner
import com.stacklens.application.service.RexStageRunner

data class PipelineDeps(
    val copilot: CopilotStreamingPort,
    val copilotConfig: CopilotConfigPort,
    val stackLensConfig: StackLensConfigPort,
    val dbConfigRepo: DatabaseConfigPort,
    val dbSchemaPort: DatabaseSchemaPort,
    val aiConfigRepo: AiConfigPort,
    val i18n: I18nPort,
    val filePort: FileListingReadWriteDeletePort,
    val classpathResource: ClasspathResourcePort,
    val processPort: AgentProcessPort,
    val platformPort: PlatformPort,
    val clock: ClockPort,
    val stageOutputWriter: StageOutputPort,
    val ivanStageRunner: IvanStageRunner,
    val rexStageRunner: RexStageRunner,
)
