package com.stacklens.application.service.loadtest

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.loadtest.K6ScriptTransformerAdapter
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.file.TempFileReadWriteDeletePort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import com.stacklens.application.port.out.process.ExecutableLookupPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.application.service.loadtest.LoadTestAi
import org.mockito.kotlin.mock

internal fun testK6ProcessController(
    analysisRepo: LoadTestAnalysisPort,
    aiService: LoadTestAi,
    i18n: I18nPort,
    processRunPort: ProcessRunPort,
    localBaseUrl: LocalBaseUrlPort,
    scriptTransformer: K6ScriptTransformerAdapter,
    outputFilter: OutputFilterPort = mock(),
    executables: ExecutableLookupPort,
    fileSystem: TempFileReadWriteDeletePort = JavaFileSystemAdapter(),
    runRegistry: K6RunRegistry = K6RunRegistry(),
    launcher: K6ProcessLauncher = K6ProcessLauncher(processRunPort, fileSystem, runRegistry),
    statusReporter: K6StatusReporter =
        K6StatusReporter(
            analysisRepo = analysisRepo,
            outputFilter = outputFilter,
            runRegistry = runRegistry,
            summaryFileReader = K6SummaryFileReader(fileSystem),
        ),
): K6ProcessController =
    K6ProcessController(
        analysisRepo = analysisRepo,
        aiService = aiService,
        i18n = i18n,
        processRunPort = processRunPort,
        localBaseUrl = localBaseUrl,
        scriptTransformer = scriptTransformer,
        outputFilter = outputFilter,
        executables = executables,
        runRegistry = runRegistry,
        launcher = launcher,
        statusReporter = statusReporter,
    )
