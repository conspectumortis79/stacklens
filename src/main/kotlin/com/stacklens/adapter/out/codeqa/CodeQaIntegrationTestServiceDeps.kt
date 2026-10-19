package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.codeqa.ComposeFileSelector
import com.stacklens.adapter.out.codeqa.DockerTestRunner
import com.stacklens.adapter.out.codeqa.GitCloneOrchestrator
import com.stacklens.adapter.out.codeqa.IntegrationCloneUrlBuilder
import com.stacklens.adapter.out.codeqa.IntegrationTestPlanQuery
import com.stacklens.adapter.out.codeqa.IntegrationTestStream
import com.stacklens.adapter.out.codeqa.IntegrationTestStreamRegistry
import com.stacklens.adapter.out.codeqa.RepoFileTreeLister
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TempDirectoryTrackerPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.file.TempFileWritePort
import com.stacklens.application.port.out.platform.SystemInfoPort
import com.stacklens.application.port.out.process.SleeperPort

data class CodeQaIntegrationTestServiceDeps(
    val codeQaRepo: CodeQaAnalysisPort,
    val jsonCodec: JsonCodecPort,
    val filePort: TempFileWritePort,
    val sleeper: SleeperPort,
    val tempDirTracker: TempDirectoryTrackerPort,
    val systemInfo: SystemInfoPort,
    val cloneUrlBuilder: IntegrationCloneUrlBuilder,
    val gitCloneOrchestrator: GitCloneOrchestrator,
    val dockerTestRunner: DockerTestRunner,
    val composeFileSelector: ComposeFileSelector,
    val planQuery: IntegrationTestPlanQuery,
    val repoFileTreeLister: RepoFileTreeLister,
    val streamFormatter: IntegrationTestStream,
    val streamRegistry: IntegrationTestStreamRegistry,
    val asyncExecutor: AsyncExecutorPort,
)
