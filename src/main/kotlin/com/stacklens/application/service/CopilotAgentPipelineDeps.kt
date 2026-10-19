package com.stacklens.application.service

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.PullRequestDiffPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.file.FileListingReadWriteDeletePort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.AgentProcessPort

data class CopilotAgentPipelineDeps(
    val copilot: CopilotStreamingPort,
    val localRepoConfigRepo: LocalRepoConfigPort,
    val dbConfigRepo: DatabaseConfigPort,
    val dbScanPort: DatabaseScanPort,
    val i18n: I18nPort,
    val aiConfigRepo: AiConfigPort,
    val outputPersister: AgentOutputPersister,
    val jsonCodec: JsonCodecPort,
    val copilotConfig: CopilotConfigPort,
    val stackLensConfig: StackLensConfigPort,
    val processPort: AgentProcessPort,
    val platformPort: PlatformPort,
    val filePort: FileListingReadWriteDeletePort,
    val classpathResource: ClasspathResourcePort,
    val pullRequestDiffPort: PullRequestDiffPort,
    val repoScanner: RepoScanner,
    val aiUsageContext: AiUsageContextPort,
    val ivanStageRunner: IvanStageRunner,
    val rexStageRunner: RexStageRunner,
    val ticketDetector: AgentTicketNumberDetector,
)
