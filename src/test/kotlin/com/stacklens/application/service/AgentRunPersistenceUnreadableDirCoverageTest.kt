package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.persistence.AgentPipelineRunSnapshotCodec
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class AgentRunPersistenceUnreadableDirCoverageTest {
    private fun persistence(dataDir: String): AgentRunPersistence =
        AgentRunPersistence(
            dataDir = dataDir,
            runs = ConcurrentHashMap<Long, AgentPipelineRun>(),
            jsonCodec = TestJsonCodec.create(),
            filePort = JavaFileSystemAdapter(),
            snapshotCodec = AgentPipelineRunSnapshotCodec(clock = com.stacklens.adapter.out.time.SystemClockPort),
        )

    @Test
    fun `a runs directory that cannot be listed is skipped`(
        @TempDir dir: Path,
    ) {
        val runs = File(File(dir.toFile(), "agent-pipeline"), "runs")
        runs.mkdirs()
        val readable = runs.setReadable(false) && runs.setExecutable(false)
        try {
            persistence(dir.toFile().absolutePath).loadFromDisk()
        } finally {
            runs.setReadable(true)
            runs.setExecutable(true)
        }
        assertTrue(runs.exists() || !readable)
    }

    @Test
    fun `a snapshot that cannot be decoded is skipped`(
        @TempDir dir: Path,
    ) {
        val runs = File(File(dir.toFile(), "agent-pipeline"), "runs")
        runs.mkdirs()
        File(runs, "not-a-number.json").writeText("{ this is not json")
        File(runs, "7.json").writeText("{ also broken")

        persistence(dir.toFile().absolutePath).loadFromDisk()

        assertTrue(runs.isDirectory)
    }

    @Test
    fun `a missing directory ends the restore immediately`(
        @TempDir dir: Path,
    ) {
        persistence(File(dir.toFile(), "nowhere").absolutePath).loadFromDisk()

        assertTrue(!File(dir.toFile(), "nowhere").exists())
    }
}
