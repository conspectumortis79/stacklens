package com.stacklens.application.service.loadtest

import com.stacklens.application.port.inbound.loadtest.K6StatusResult
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import org.slf4j.LoggerFactory

class K6StatusReporter(
    private val analysisRepo: LoadTestAnalysisPort,
    private val outputFilter: OutputFilterPort,
    private val runRegistry: K6RunRegistry,
    private val summaryFileReader: K6SummaryFileReader,
) {
    private val log = LoggerFactory.getLogger(K6StatusReporter::class.java)

    fun report(analysisId: Long): K6StatusResult {
        val run =
            runRegistry.find(analysisId)
                ?: run {
                    val analysis = analysisRepo.findLoadTestAnalysisById(analysisId)
                    return if (analysis?.k6ExitCode != null) {
                        K6StatusResult(
                            running = false,
                            exitCode = analysis.k6ExitCode,
                            output = analysis.k6Output ?: "",
                            error = analysis.k6Error ?: "",
                            summaryJson = analysis.k6SummaryJson,
                        )
                    } else {
                        K6StatusResult(running = false, exitCode = null, output = "", error = "")
                    }
                }
        val alive = run.process.isAlive
        val out = synchronized(run.stdoutBuf) { run.stdoutBuf.toString() }
        val err = synchronized(run.stderrBuf) { run.stderrBuf.toString() }
        val code = if (alive) null else run.process.exitValue()
        val summaryJson =
            if (!alive) {
                summaryFileReader.readAndDelete(run.summaryFile)
            } else {
                null
            }
        if (!alive) {
            runRegistry.remove(analysisId)
            runCatching {
                val analysis = analysisRepo.findLoadTestAnalysisById(analysisId)
                if (analysis != null) {
                    val (filteredOut, filteredErr) = filter(out, err)
                    val scriptHeader = run.scriptHeader
                    val combinedOut =
                        if (scriptHeader != null) {
                            scriptHeader + "\n" + filteredOut
                        } else {
                            filteredOut
                        }
                    analysis.k6Output = combinedOut.take(500_000)
                    analysis.k6Error = filteredErr.take(100_000)
                    analysis.k6ExitCode = code
                    analysis.k6SummaryJson = summaryJson
                    analysisRepo.saveLoadTestAnalysis(analysis)
                }
            }.onFailure { log.warn("[k6] k6 run results for analysisId={} could not be saved: {}", analysisId, it.message) }
        }
        return K6StatusResult(running = alive, exitCode = code, output = out, error = err, summaryJson = summaryJson)
    }

    private fun filter(
        stdout: String,
        stderr: String,
    ): Pair<String, String> {
        if (!outputFilter.enabled) return stdout to stderr
        val cmd = listOf("k6", "run")
        val out = if (stdout.isNotEmpty()) outputFilter.filter(cmd, stdout, "").text else stdout
        val err = if (stderr.isNotEmpty()) outputFilter.filter(cmd, stderr, "").text else stderr
        return out to err
    }
}
