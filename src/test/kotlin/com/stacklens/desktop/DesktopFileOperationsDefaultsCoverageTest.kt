package com.stacklens.desktop

import javafx.scene.control.Alert
import javafx.stage.FileChooser
import javafx.stage.Stage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File

private fun tempFile(): File =
    java.nio.file.Files
        .createTempFile("stacklens-desktop", ".bin")
        .toFile()

private class CapturingFxRunner : JavaFxRunner {
    private val scheduled = java.util.concurrent.CountDownLatch(1)
    val blocks = mutableListOf<() -> Unit>()

    override fun runOnFxThread(block: () -> Unit) {
        synchronized(blocks) { blocks.add(block) }
        scheduled.countDown()
    }

    fun awaitScheduled() {
        assertTrue(scheduled.await(15, java.util.concurrent.TimeUnit.SECONDS), "no alert was scheduled")
    }
}

private fun awaitFile(
    file: File,
    expectContent: Boolean,
) {
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
        val matches = if (expectContent) file.length() > 0L else file.length() == 0L
        if (matches) return
        Thread.sleep(50)
    }
    assertTrue(!expectContent || file.length() > 0L, "the download did not finish in time")
}

class DesktopFileSaverDefaultAlertsCoverageTest {
    private val stage = mock<Stage>()

    @Test
    fun `the default file chooser factory is used when saving a file`() {
        val runner = FakeJavaFxRunner()
        val saver = DesktopFileSaver(stage = stage, fxRunner = runner)
        Mockito.mockConstruction(FileChooser::class.java).use { _ ->
            saver.saveFile("notes.txt", "aGk=")
            runner.submitted.last().invoke()
        }

        assertEquals(1, runner.submitted.size)
    }

    @Test
    fun `the default success alert reports a written file`() {
        val target = tempFile()
        val saver = DesktopFileSaver(stage = stage)
        Mockito.mockConstruction(Alert::class.java).use { alerts ->
            saver.saveFileBody("notes.txt", "aGk=", target)

            assertEquals(1, alerts.constructed().size)
            verify(alerts.constructed().first()).title = "Saved successfully"
        }
        assertEquals("hi", target.readText())
        assertTrue(target.delete())
    }

    @Test
    fun `the default error alert reports an undecodable payload`() {
        val target = tempFile()
        val saver = DesktopFileSaver(stage = stage)
        Mockito.mockConstruction(Alert::class.java).use { alerts ->
            saver.saveFileBody("notes.txt", "not base64 at all", target)

            assertEquals(1, alerts.constructed().size)
            verify(alerts.constructed().first()).title = "Error saving"
        }
        assertTrue(target.delete())
    }
}

class DesktopFileDownloaderDefaultChooserCoverageTest {
    private val stage = mock<Stage>()
    private val runner = FakeJavaFxRunner()

    @Test
    fun `the default file chooser factory is used when downloading a tool`() {
        val downloader = DesktopFileDownloader(stage = stage, port = 8090, fxRunner = runner)
        Mockito.mockConstruction(FileChooser::class.java).use { _ ->
            downloader.downloadTool("tool.jar", "tool-1")
            runner.submitted.last().invoke()
        }

        verify(stage).toFront()
    }

    @Test
    fun `the default file chooser factory is used when downloading a url`() {
        val downloader = DesktopFileDownloader(stage = stage, port = 8090, fxRunner = runner)
        Mockito.mockConstruction(FileChooser::class.java).use { _ ->
            downloader.downloadUrl("/api/download", "report.pdf")
            runner.submitted.last().invoke()
        }

        verify(stage).toFront()
    }
}

class DesktopFileDownloaderTransferCoverageTest {
    private val server = MockWebServer()
    private val stage = mock<Stage>()

    @BeforeEach
    fun startServer() {
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `a tool download stores the exported bytes on disk`() {
        val target = tempFile()
        server.enqueue(MockResponse().setBody("jar-bytes"))
        val downloader = DesktopFileDownloader(stage = stage, port = server.port)

        downloader.downloadToolBody("tool.jar", "tool-1", target)

        awaitFile(target, expectContent = true)
        assertEquals("jar-bytes", target.readText())
        assertTrue(target.delete())
    }

    @Test
    fun `a failed tool download is logged instead of thrown`() {
        val target = tempFile()
        val stopped = MockWebServer()
        stopped.start()
        val port = stopped.port
        stopped.shutdown()
        val downloader = DesktopFileDownloader(stage = stage, port = port)

        downloader.downloadToolBody("tool.jar", "missing", target)

        awaitFile(target, expectContent = false)
        assertTrue(target.delete())
    }

    @Test
    fun `a url download shows the default success alert`() {
        val target = tempFile()
        val runner = CapturingFxRunner()
        server.enqueue(MockResponse().setBody("downloaded"))
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = server.port,
                fxRunner = runner,
            )

        downloader.downloadUrlBody("/report.txt", target)
        runner.awaitScheduled()

        Mockito.mockConstruction(Alert::class.java).use { alerts ->
            runner.blocks.last().invoke()

            assertEquals(1, alerts.constructed().size)
            verify(alerts.constructed().first()).title = "Saved"
        }
        assertEquals("downloaded", target.readText())
        assertTrue(target.delete())
    }

    @Test
    fun `a failing url download shows the default error alert`() {
        val target = tempFile()
        val runner = CapturingFxRunner()
        val stopped = MockWebServer()
        stopped.start()
        val port = stopped.port
        stopped.shutdown()
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = port,
                fxRunner = runner,
            )

        downloader.downloadUrlBody("/missing.txt", target)
        runner.awaitScheduled()

        Mockito.mockConstruction(Alert::class.java).use { alerts ->
            runner.blocks.last().invoke()

            assertEquals(1, alerts.constructed().size)
            verify(alerts.constructed().first()).title = "Download failed"
        }
        assertTrue(target.delete())
    }
}
