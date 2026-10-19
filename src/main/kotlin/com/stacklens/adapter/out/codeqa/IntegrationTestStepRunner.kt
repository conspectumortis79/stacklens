package com.stacklens.adapter.out.codeqa
import com.stacklens.adapter.out.codeqa.CodeQaIntegrationTestServiceDeps
import com.stacklens.adapter.out.codeqa.IntegrationTestStreamPlan
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeQaAnalysis
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
class IntegrationTestStepRunner(
    private val deps: CodeQaIntegrationTestServiceDeps,
) {
    private val codeQaRepo get() = deps.codeQaRepo
    private val filePort get() = deps.filePort
    private val sleeper get() = deps.sleeper
    private val cloneUrlBuilder get() = deps.cloneUrlBuilder
    private val gitCloneOrchestrator get() = deps.gitCloneOrchestrator
    private val dockerTestRunner get() = deps.dockerTestRunner
    private val composeFileSelector get() = deps.composeFileSelector
    private val planQuery get() = deps.planQuery
    private val repoFileTreeLister get() = deps.repoFileTreeLister
    private val streamFormatter get() = deps.streamFormatter

    private val log = LoggerFactory.getLogger(IntegrationTestStepRunner::class.java)

    fun stopDockerServices(
        dockerStarted: Boolean,
        composeDir: File,
    ) {
        if (dockerStarted) dockerTestRunner.stopServices(composeDir)
    }

    fun runCloneUrlStep(
        mrData: MrFetchPort.MrData,
        analysis: CodeQaAnalysis,
        sb: StringBuilder,
    ): String? {
        val step = IntegrationTestStep.CLONE_URL
        streamFormatter.emitStep(sb, step.number, step.label, "running", null)
        val cloneUrl = cloneUrlBuilder.buildCloneUrl(mrData, analysis)
        streamFormatter.appendTestLog(analysis, "Clone URL determined")
        codeQaRepo.saveCodeQaAnalysis(analysis)
        streamFormatter.emitStep(sb, step.number, step.label, "done", "Clone URL determined")
        return cloneUrl
    }

    fun runCloneStep(
        cloneUrl: String,
        mrData: MrFetchPort.MrData,
        tmpDir: File,
        analysis: CodeQaAnalysis,
        sb: StringBuilder,
    ) {
        val step = IntegrationTestStep.CLONE_BRANCH
        streamFormatter.emit(sb, "\n${step.progressPrefix()} Cloning MR branch '${mrData.sourceBranch}' into temp folder...\n")
        streamFormatter.emitStep(sb, step.number, step.label, "running", "branch=${mrData.sourceBranch}")
        val cloneResult = gitCloneOrchestrator.cloneBranch(cloneUrl, tmpDir, mrData)
        if (!cloneResult.success) throw IllegalStateException(cloneResult.errorMessage ?: "Clone failed")
        streamFormatter.emit(sb, "✅ Branch '${mrData.sourceBranch}' cloned into ${tmpDir.absolutePath}\n")
        streamFormatter.appendTestLog(analysis, "Branch '${mrData.sourceBranch}' cloned into ${tmpDir.name}")
        streamFormatter.emitStep(sb, step.number, step.label, "done", "Cloned into ${tmpDir.name}")
        codeQaRepo.saveCodeQaAnalysis(analysis)
    }

    fun runComposeDiscoveryStep(
        mrData: MrFetchPort.MrData,
        tmpDir: File,
        analysis: CodeQaAnalysis,
        sb: StringBuilder,
    ): File? {
        val step = IntegrationTestStep.COMPOSE_DISCOVERY
        streamFormatter.emit(sb, "\n${step.progressPrefix()} ${step.label}...\n")
        streamFormatter.emitStep(sb, step.number, step.label, "running", null)
        val composeFile =
            composeFileSelector.findAndSelect(tmpDir, mrData) { line -> streamFormatter.emit(sb, line) }
        if (composeFile == null) {
            streamFormatter.emit(sb, "⚠️ No docker-compose.yml found – tests not possible.\n")
            IntegrationTestStep
                .startingFrom(step)
                .forEach {
                    streamFormatter.emitStep(
                        sb,
                        it.number,
                        it.label,
                        "skipped",
                        if (it == step) "No docker-compose.yml found" else "skipped",
                    )
                }
            return null
        }
        streamFormatter.emitStep(
            sb,
            step.number,
            step.label,
            "done",
            composeFile.relativeTo(tmpDir).path,
        )
        return composeFile
    }

    fun runPlanStep(
        mrData: MrFetchPort.MrData,
        composeDir: File,
        composeFile: File,
        tmpDir: File,
        analysis: CodeQaAnalysis,
        sb: StringBuilder,
    ): IntegrationTestPlan? {
        val step = IntegrationTestStep.TEST_PLAN
        streamFormatter.emit(sb, "\n${step.progressPrefix()} ${step.label}...\n")
        streamFormatter.emitStep(sb, step.number, step.label, "running", null)
        val composeContent = filePort.readText(composeFile).take(30_000)
        val repoFileList = repoFileTreeLister.buildFileList(tmpDir)
        val testInfraFiles = planQuery.detectTestInfraFiles(composeDir)
        val testPlan =
            planQuery.queryForTestPlan(
                mrData,
                composeContent,
                repoFileList,
                testInfraFiles,
                composeDir.relativeTo(tmpDir).path,
            )
        if (testPlan.affectedServices.isEmpty()) {
            streamFormatter.emit(sb, "⚠️ No affected services detected – skipped.\n")
            streamFormatter.emitStep(sb, step.number, step.label, "done", "No affected services")
            IntegrationTestStep
                .startingFrom(IntegrationTestStep.DOCKER_UP)
                .forEach { streamFormatter.emitStep(sb, it.number, it.label, "skipped", "skipped") }
            return null
        }
        streamFormatter.appendTestLog(analysis, "Services: ${testPlan.affectedServices.joinToString(", ")}")
        streamFormatter.appendTestLog(analysis, "Strategy: ${testPlan.testStrategy.ifBlank { "n/a" }}")
        if (testPlan.testTargets.isNotEmpty()) {
            streamFormatter.appendTestLog(analysis, "Targets: ${testPlan.testTargets.joinToString(", ")}")
        }
        if (testPlan.reasoning.isNotBlank()) {
            streamFormatter.appendTestLog(analysis, "AI plan: ${testPlan.reasoning.take(200)}")
        }
        streamFormatter.emitPlan(
            sb,
            IntegrationTestStreamPlan(
                services = testPlan.affectedServices,
                testCommands = testPlan.testCommands,
                projectTypes = testPlan.projectTypes,
                testStrategy = testPlan.testStrategy,
                testTargets = testPlan.testTargets,
                reasoning = testPlan.reasoning,
                waitSeconds = testPlan.waitSeconds,
            ),
        )
        streamFormatter.emitStep(
            sb,
            step.number,
            step.label,
            "done",
            testPlan.affectedServices.joinToString(", "),
        )
        codeQaRepo.saveCodeQaAnalysis(analysis)
        return testPlan
    }

    fun runDockerUpStep(
        composeDir: File,
        testPlan: IntegrationTestPlan,
        analysis: CodeQaAnalysis,
        sb: StringBuilder,
    ): Boolean {
        val step = IntegrationTestStep.DOCKER_UP
        streamFormatter.emit(sb, "\n${step.progressPrefix()} ${step.label}...\n")
        streamFormatter.emitStep(
            sb,
            step.number,
            step.label,
            "running",
            testPlan.affectedServices.joinToString(", "),
        )
        val upResult =
            dockerTestRunner.startServices(
                composeDir,
                testPlan.affectedServices,
                onLine = { line -> streamFormatter.emit(sb, line) },
            )
        if (!upResult.success) return false
        val waitSec = testPlan.waitSeconds.coerceIn(5, 120)
        streamFormatter.emit(sb, "\nWaiting ${waitSec}s for services\n")
        streamFormatter.emitWaiting(sb, waitSec, testPlan.affectedServices)
        streamFormatter.emitStep(sb, step.number, step.label, "waiting", "Waiting ${waitSec}s")
        sleeper.sleep(waitSec * 1000L)
        streamFormatter.emitStep(sb, step.number, step.label, "done", "Services started")

        testPlan.affectedServices.forEach { svc ->
            streamFormatter.emitService(sb, svc, "ready")
        }
        return true
    }

    fun runTestsStep(
        composeDir: File,
        testPlan: IntegrationTestPlan,
        analysis: CodeQaAnalysis,
        sb: StringBuilder,
    ): List<DockerTestRunner.TestOutput> {
        val step = IntegrationTestStep.RUN_TESTS
        streamFormatter.emit(sb, "\n${step.progressPrefix()} ${step.label}...\n")
        log.info(
            "[MR-IntegrationTest] Running {} test command(s) against {} (strategy={}, targets={})",
            testPlan.testCommands.size,
            testPlan.affectedServices.joinToString(","),
            testPlan.testStrategy,
            testPlan.testTargets,
        )
        streamFormatter.emitTestsRunning(sb, testPlan.testCommands)
        streamFormatter.emitStep(sb, step.number, step.label, "running", "${testPlan.testCommands.size} test(s)")
        val testOutputs =
            dockerTestRunner.runTests(
                composeDir,
                testPlan.testCommands,
                onLine = { line -> streamFormatter.emit(sb, line) },
                onTestStart = { idx, cmd ->
                    streamFormatter.emitTestRunning(sb, idx, cmd)
                },
                onTestEnd = { idx, cmd, exitCode ->
                    streamFormatter.emitTestFinished(sb, idx, cmd, exitCode)
                },
            )
        val passed = testOutputs.count { it.passed }
        val total = testOutputs.size
        streamFormatter.appendTestLog(analysis, "Tests completed: $passed/$total")
        testOutputs.forEachIndexed { idx, out ->
            if (out.passed) {
                log.info("[MR-IntegrationTest] Test #{}/{} PASS: {}", idx + 1, total, out.command)
            } else {
                val stdoutTail = out.result.stdout.takeLast(400)
                val stderrTail = out.result.stderr.takeLast(400)
                log.error(
                    "[MR-IntegrationTest] Test #{}/{} FAIL (exit={}): {}\n--- stdout tail ---\n{}\n--- stderr tail ---\n{}",
                    idx + 1,
                    total,
                    out.result.exitCode,
                    out.command,
                    stdoutTail,
                    stderrTail,
                )
                streamFormatter.appendTestLog(
                    analysis,
                    "✗ Test ${idx + 1}/$total failed (exit=${out.result.exitCode}): ${out.command}",
                )
                if (stderrTail.isNotBlank()) streamFormatter.appendTestLog(analysis, "  stderr: ${stderrTail.take(200)}")
                if (stdoutTail.isNotBlank()) streamFormatter.appendTestLog(analysis, "  stdout: ${stdoutTail.take(200)}")
            }
        }
        streamFormatter.emitTestsSummary(sb, passed, total, testOutputs.map { it.command to it.passed })
        streamFormatter.emitStep(
            sb,
            step.number,
            step.label,
            if (passed == total) "done" else "failed",
            "$passed/$total passed",
        )
        return testOutputs
    }

    fun runSummaryStep(
        analysis: CodeQaAnalysis,
        mrData: MrFetchPort.MrData,
        testPlan: IntegrationTestPlan,
        testOutputs: List<DockerTestRunner.TestOutput>,
        passed: Int,
        total: Int,
        sb: StringBuilder,
    ) {
        val step = IntegrationTestStep.SUMMARISE
        streamFormatter.emit(sb, "\n${step.progressPrefix()} ${step.label}...\n")
        streamFormatter.emitStep(sb, step.number, step.label, "running", null)
        val summary = planQuery.summarizeResults(mrData, testPlan, testOutputs)
        analysis.integrationTestResult = summary
        analysis.integrationTestStatus = AnalysisStatus.COMPLETED
        codeQaRepo.saveCodeQaAnalysis(analysis)
        streamFormatter.emitStep(sb, step.number, step.label, "done", null)
        streamFormatter.emit(sb, "\n✅ Integration tests completed ($passed/$total)\n")
    }

    fun finalizeSkipped(
        analysis: CodeQaAnalysis,
        resultMarkdown: String,
    ) {
        analysis.integrationTestStatus = AnalysisStatus.COMPLETED
        analysis.integrationTestResult = resultMarkdown
        codeQaRepo.saveCodeQaAnalysis(analysis)
    }
}
