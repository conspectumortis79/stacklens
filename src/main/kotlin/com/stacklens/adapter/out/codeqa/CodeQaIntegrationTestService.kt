package com.stacklens.adapter.out.codeqa

import com.stacklens.adapter.out.codeqa.IntegrationTestPlan
import com.stacklens.adapter.out.codeqa.IntegrationTestStepRunner
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.mr.CodeQaIntegrationTestPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.util.quietly
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File

@Component
class CodeQaIntegrationTestService
    @Autowired
    constructor(
        private val deps: CodeQaIntegrationTestServiceDeps,
        private val steps: IntegrationTestStepRunner,
    ) : CodeQaIntegrationTestPort {
        internal constructor(deps: CodeQaIntegrationTestServiceDeps) : this(deps, IntegrationTestStepRunner(deps))

        private val codeQaRepo get() = deps.codeQaRepo
        private val filePort get() = deps.filePort
        private val systemInfo get() = deps.systemInfo

        private val log = LoggerFactory.getLogger(CodeQaIntegrationTestService::class.java)

        private val streamFormatter get() = deps.streamFormatter
        private val tempDirTracker = deps.tempDirTracker
        private val streamRegistry = deps.streamRegistry

        override fun getStreamingOutput(id: Long): String? = streamRegistry.read(id)

        typealias TestPlan = IntegrationTestPlan

        override fun runIntegrationTests(
            analysisId: Long,
            mrData: MrFetchPort.MrData,
        ) = deps.asyncExecutor.submit("analysisTaskExecutor") { runIntegrationTestsNow(analysisId, mrData) }

        internal fun runIntegrationTestsNow(
            analysisId: Long,
            mrData: MrFetchPort.MrData,
        ) {
            var analysis = codeQaRepo.findCodeQaAnalysisById(analysisId) ?: return
            analysis.integrationTestStatus = AnalysisStatus.RUNNING
            val sb = streamRegistry.open(analysisId)
            streamFormatter.appendTestLog(analysis, "Integration tests started")
            analysis = codeQaRepo.saveCodeQaAnalysis(analysis)

            val tmpDir: java.io.File = filePort.createTempDirectory("stacklens-mr-test-$analysisId")
            tempDirTracker.register(tmpDir)
            var dockerStarted = false
            var composeDir: File = tmpDir
            try {
                streamFormatter.emit(sb, streamFormatter.header())
                streamFormatter.emit(sb, streamFormatter.environmentBanner())
                streamFormatter.emit(sb, "Working dir: ${tmpDir.absolutePath}\n")
                streamFormatter.emit(sb, "Changed files in MR: ${mrData.changedFiles.size}\n")
                streamFormatter.emit(sb, "MR branch to test: '${mrData.sourceBranch}'\n\n")
                streamFormatter.emitStarted(
                    sb,
                    branch = mrData.sourceBranch,
                    targetBranch = mrData.targetBranch,
                    changedFiles = mrData.changedFiles.size,
                    tmpDir = tmpDir.absolutePath,
                    os = systemInfo.hostSystem().osName,
                )
                val cloneUrl =
                    steps.runCloneUrlStep(mrData, analysis, sb).also { analysis = codeQaRepo.findCodeQaAnalysisById(analysisId) ?: analysis }
                        ?: throw IllegalStateException("Could not determine clone URL for: ${mrData.mrUrl}")

                steps.runCloneStep(cloneUrl, mrData, tmpDir, analysis, sb).also {
                    analysis = codeQaRepo.findCodeQaAnalysisById(analysisId) ?: analysis
                }

                val composeFile = steps.runComposeDiscoveryStep(mrData, tmpDir, analysis, sb)
                if (composeFile == null) {
                    steps.finalizeSkipped(analysis, "## ⚠️ Integration tests skipped\n\nNo `docker-compose.yml` found.")
                    return
                }
                composeDir = composeFile.parentFile

                val testPlan = steps.runPlanStep(mrData, composeDir, composeFile, tmpDir, analysis, sb)
                if (testPlan == null) {
                    steps.finalizeSkipped(analysis, "## ⚠️ Integration tests skipped\n\nNo affected services detected.")
                    return
                }

                val dockerUpOk = steps.runDockerUpStep(composeDir, testPlan, analysis, sb)
                if (!dockerUpOk) throw IllegalStateException("docker-compose up failed")
                dockerStarted = true

                val testOutputs = steps.runTestsStep(composeDir, testPlan, analysis, sb)
                val passed = testOutputs.count { it.passed }
                val total = testOutputs.size

                steps.runSummaryStep(analysis, mrData, testPlan, testOutputs, passed, total, sb)
            } catch (e: Exception) {
                log.error("[MR-IntegrationTest] Error: {}", e.message, e)
                val fresh = codeQaRepo.findCodeQaAnalysisById(analysisId) ?: analysis
                fresh.integrationTestStatus = AnalysisStatus.FAILED
                fresh.integrationTestResult =
                    "## ❌ Failed\n\n```\n${e.message?.take(1500)}\n```"
                codeQaRepo.saveCodeQaAnalysis(fresh)
                streamFormatter.emit(sb, "\n❌ ERROR: ${e.message?.take(500)}\n")
                streamFormatter.emitError(sb, e.message?.take(500) ?: "")
            } finally {
                steps.stopDockerServices(dockerStarted, composeDir)
                quietly { filePort.deleteDirectoryRecursively(tmpDir) }
                tempDirTracker.unregister(tmpDir)
                streamRegistry.close(analysisId)
            }
        }
    }
