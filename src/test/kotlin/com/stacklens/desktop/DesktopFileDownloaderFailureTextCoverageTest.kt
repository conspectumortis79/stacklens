package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopFileDownloaderFailureTextCoverageTest {
    private val stage = mock<javafx.stage.Stage>()

    private class Recorder : JavaFxRunner {
        val blocks = mutableListOf<() -> Unit>()

        override fun runOnFxThread(block: () -> Unit) {
            blocks += block
        }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(condition(), "the download thread never reached the callback")
    }

    private fun await(latch: CountDownLatch) {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "the download thread never reached its failure path")
    }

    @Test
    fun `the caller gets a placeholder when the failure has no message`() {
        val latch = CountDownLatch(1)
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 8099,
                fxRunner = Recorder(),
                downloader = { _: URL, _: File -> throw IllegalStateException() },
            )
        var reported: String? = null

        downloader.downloadUrlBody("/api/x", File("target.kt"), onError = {
            reported = it
            latch.countDown()
        })
        await(latch)

        assertEquals("Unknown error", reported)
    }

    @Test
    fun `the caller sees the real message when there is one`() {
        val latch = CountDownLatch(1)
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 8099,
                fxRunner = Recorder(),
                downloader = { _: URL, _: File -> throw IllegalStateException("connection reset") },
            )
        var reported: String? = null

        downloader.downloadUrlBody("/api/x", File("target.kt"), onError = {
            reported = it
            latch.countDown()
        })
        await(latch)

        assertEquals("connection reset", reported)
    }

    @Test
    fun `without a callback the dialog text falls back too`() {
        val latch = CountDownLatch(1)
        val runner = Recorder()
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 8099,
                fxRunner = runner,
                downloader = { _: URL, _: File -> throw IllegalStateException() },
            )

        downloader.downloadUrlBody("/api/x", File("target.kt"))
        awaitUntil { runner.blocks.isNotEmpty() }

        val dialog = runCatching { runner.blocks.last().invoke() }
        assertEquals(false, dialog.isSuccess, "the alert needs a javafx toolkit, so it must fail outside the ui thread")
    }

    @Test
    fun `a successful download without a callback shows the info dialog`() {
        val latch = CountDownLatch(1)
        val runner = Recorder()
        val target = File.createTempFile("downloaded", ".txt")
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 8099,
                fxRunner = runner,
                downloader = { _: URL, file: File -> file.writeText("payload") },
            )

        downloader.downloadUrlBody("/api/x", target)
        awaitUntil { runner.blocks.isNotEmpty() }
        val info = runCatching { runner.blocks.last().invoke() }

        assertEquals(false, info.isSuccess)
        target.delete()
    }

    @Test
    fun `a success callback wins over the info dialog`() {
        val latch = CountDownLatch(1)
        val target = File.createTempFile("downloaded", ".txt")
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 8099,
                fxRunner = Recorder(),
                downloader = { _: URL, _: File -> },
            )
        var message: String? = null

        downloader.downloadUrlBody("/api/x", target, onSuccess = {
            message = it
            latch.countDown()
        })
        await(latch)

        assertEquals("File saved: ${target.absolutePath}", message)
        target.delete()
    }

    @Test
    fun `a cancelled file dialog skips the download entirely`() {
        var downloads = 0
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 8099,
                fxRunner = Recorder(),
                downloader = { _: URL, _: File -> downloads++ },
            )

        downloader.downloadUrlBody("/api/x", null)

        assertEquals(0, downloads)
    }
}
