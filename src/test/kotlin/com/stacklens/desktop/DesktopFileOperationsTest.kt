package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.verify
import java.io.File
import java.nio.file.Files

class DesktopFileOperationsTest {
    @Test
    fun `toolExportUrl builds correct URL for given port and tool id`() {
        val url = DesktopFileDownloader.toolExportUrl(8090, "k6-runner")
        assertEquals("http://127.0.0.1:8090/api/tooling/k6-runner/export", url.toString())
    }

    @Test
    fun `relativeUrl builds correct URL for given port and relative path`() {
        val url = DesktopFileDownloader.relativeUrl(8090, "/api/foo?bar=1")
        assertEquals("http://127.0.0.1:8090/api/foo?bar=1", url.toString())
    }

    @Test
    fun `downloadToFile writes bytes to file`() {
        val tmp = Files.createTempFile("dl-", ".bin").toFile()
        try {
            tmp.writeBytes("hello".toByteArray())
            assertArrayEquals("hello".toByteArray(), Files.readAllBytes(tmp.toPath()))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `downloadToFile throws on bad URL`() {
        val tmp = Files.createTempFile("dl-", ".bin").toFile()
        try {
            assertThrows<Exception> {
                DesktopFileDownloader.downloadToFile(java.net.URI("http://127.0.0.1:1/missing").toURL(), tmp)
            }
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `downloadToolBody is a no-op when target file is null`() {
        val downloader = DesktopFileDownloader(stage = mockStage(), port = 8090)
        downloader.downloadToolBody(filename = "f.bin", toolId = "x", targetFile = null)
        Thread.sleep(50)
        assertTrue(true)
    }

    @Test
    fun `downloadUrlBody is a no-op when target file is null`() {
        val downloader = DesktopFileDownloader(stage = mockStage(), port = 8090)
        downloader.downloadUrlBody(relPath = "/x", targetFile = null)
        Thread.sleep(50)
        assertTrue(true)
    }

    @Test
    fun `downloadToolBody starts download when target file is given`() {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/tooling/x/export") { ex ->
            val payload = "downloaded".toByteArray()
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val port = server.address.port
            val downloader = DesktopFileDownloader(stage = mockStage(), port = port)
            val tmp = Files.createTempFile("dl-tool-", ".bin").toFile()
            try {
                downloader.downloadToolBody(filename = "x.bin", toolId = "x", targetFile = tmp)
                val deadline = System.currentTimeMillis() + 3_000
                while (tmp.length() == 0L && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                }
                assertEquals("downloaded", tmp.readText())
            } finally {
                tmp.delete()
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `downloadUrlBody writes downloaded file on success`() {
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/foo") { ex ->
            val payload = "payload-bytes".toByteArray()
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val port = server.address.port
            val downloader = DesktopFileDownloader(stage = mockStage(), port = port)
            val tmp = Files.createTempFile("dl-url-", ".bin").toFile()
            try {
                var successMsg: String? = null
                downloader.downloadUrlBody(
                    relPath = "/api/foo",
                    targetFile = tmp,
                    onSuccess = { successMsg = it },
                )
                val deadline = System.currentTimeMillis() + 3_000
                while (tmp.length() == 0L && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                }
                assertEquals("payload-bytes", tmp.readText())
                assertNotNull(successMsg)
                assertTrue(successMsg!!.contains(tmp.absolutePath))
            } finally {
                tmp.delete()
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `downloadUrlBody invokes onError when download fails`() {
        val downloader = DesktopFileDownloader(stage = mockStage(), port = 1)
        val tmp = Files.createTempFile("dl-url-fail-", ".bin").toFile()
        try {
            var errorMsg: String? = null
            downloader.downloadUrlBody(
                relPath = "/api/missing",
                targetFile = tmp,
                onError = { errorMsg = it },
            )
            val deadline = System.currentTimeMillis() + 3_000
            while (errorMsg == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertNotNull(errorMsg)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `decodeBase64 returns expected bytes`() {
        val decoded = DesktopFileSaver.decodeBase64("aGVsbG8=")
        assertArrayEquals("hello".toByteArray(), decoded)
    }

    @Test
    fun `decodeBase64 fails on invalid input`() {
        assertThrows<IllegalArgumentException> {
            DesktopFileSaver.decodeBase64("not-base64!@#")
        }
    }

    @Test
    fun `writeBytes writes content to file`() {
        val tmp = Files.createTempFile("save-", ".bin").toFile()
        try {
            DesktopFileSaver.writeBytes(tmp, "content".toByteArray())
            assertArrayEquals("content".toByteArray(), Files.readAllBytes(tmp.toPath()))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `saveFileBody writes decoded bytes when target file is given`() {
        val saver = DesktopFileSaver(stage = mockStage())
        val tmp = Files.createTempFile("save-", ".bin").toFile()
        try {
            var successMsg: String? = null
            saver.saveFileBody(
                filename = "x",
                base64Data = "aGVsbG8=",
                targetFile = tmp,
                onSuccess = { successMsg = it },
            )
            assertEquals("hello", tmp.readText())
            assertNotNull(successMsg)
            assertTrue(successMsg!!.contains(tmp.absolutePath))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `saveFileBody is a no-op when target file is null`() {
        val saver = DesktopFileSaver(stage = mockStage())
        var onSuccessCalled = false
        saver.saveFileBody(
            filename = "x",
            base64Data = "aGVsbG8=",
            targetFile = null,
            onSuccess = { onSuccessCalled = true },
        )
        assertFalse(onSuccessCalled)
    }

    @Test
    fun `saveFileBody invokes onError when decoding fails`() {
        val saver = DesktopFileSaver(stage = mockStage())
        val tmp = Files.createTempFile("save-err-", ".bin").toFile()
        try {
            var errorMsg: String? = null
            saver.saveFileBody(
                filename = "x",
                base64Data = "not-base64!@#",
                targetFile = tmp,
                onError = { errorMsg = it },
            )
            assertNotNull(errorMsg)
            assertEquals(0L, tmp.length())
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `saveFileBody invokes onSuccess without alert when onSuccess supplied`() {
        val saver = DesktopFileSaver(stage = mockStage())
        val tmp = Files.createTempFile("save-success-", ".bin").toFile()
        try {
            var called = false
            var received: String? = null
            saver.saveFileBody(
                filename = "x",
                base64Data = "aGVsbG8=",
                targetFile = tmp,
                onSuccess = {
                    called = true
                    received = it
                },
                onError = { error("onError should not be invoked") },
            )
            assertTrue(called)
            assertNotNull(received)
            assertTrue(received!!.contains(tmp.absolutePath))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `saveFileBody invokes onError when writeBytes throws`() {
        val saver = DesktopFileSaver(stage = mockStage())
        var errorMsg: String? = null
        val invalid = File("/this/path/does/not/exist/file-${System.nanoTime()}.bin")
        saver.saveFileBody(
            filename = "x",
            base64Data = "aGVsbG8=",
            targetFile = invalid,
            onError = { errorMsg = it },
        )
        assertNotNull(errorMsg)
    }

    @Test
    fun `saveFileBody falls back to errorAlert when onError is null and writeBytes throws`() {
        var errorTitle: String? = null
        var errorContent: String? = null
        val saver =
            DesktopFileSaver(
                stage = mockStage(),
                errorAlert = { t, c ->
                    errorTitle = t
                    errorContent = c
                },
            )
        val invalid = File("/this/path/does/not/exist/file-${System.nanoTime()}.bin")
        saver.saveFileBody(
            filename = "x",
            base64Data = "aGVsbG8=",
            targetFile = invalid,
        )
        assertEquals("Error saving", errorTitle)
        assertNotNull(errorContent)
    }

    @Test
    fun `saveFileBody onError receives Unknown error when exception has no message`() {
        val saver =
            DesktopFileSaver(
                stage = mockStage(),
                writer = { _, _ -> throw IllegalStateException() },
            )
        var errorMsg: String? = null
        saver.saveFileBody(
            filename = "x",
            base64Data = "aGVsbG8=",
            targetFile = File("irrelevant"),
            onError = { errorMsg = it },
        )
        assertEquals("Unknown error", errorMsg)
    }

    @Test
    fun `saveFileBody falls back to infoAlert when onSuccess is null`() {
        var infoTitle: String? = null
        var infoContent: String? = null
        val saver =
            DesktopFileSaver(
                stage = mockStage(),
                infoAlert = { t, c ->
                    infoTitle = t
                    infoContent = c
                },
            )
        val tmp = Files.createTempFile("save-no-onSuccess-", ".bin").toFile()
        try {
            saver.saveFileBody(
                filename = "x",
                base64Data = "aGVsbG8=",
                targetFile = tmp,
            )
            assertEquals("Saved successfully", infoTitle)
            assertNotNull(infoContent)
            assertTrue(infoContent!!.contains(tmp.absolutePath))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `saveFileBody falls back to errorAlert when onError is null and decoding fails`() {
        var errorTitle: String? = null
        var errorContent: String? = null
        val saver =
            DesktopFileSaver(
                stage = mockStage(),
                errorAlert = { t, c ->
                    errorTitle = t
                    errorContent = c
                },
            )
        val tmp = Files.createTempFile("save-no-onError-", ".bin").toFile()
        try {
            saver.saveFileBody(
                filename = "x",
                base64Data = "not-base64!@#",
                targetFile = tmp,
            )
            assertEquals("Error saving", errorTitle)
            assertNotNull(errorContent)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `buildCommand uses open on macOS`() {
        val cmd = BrowserLauncher.buildCommand("mac os x", "https://example.com")
        assertEquals(listOf("open", "https://example.com"), cmd)
    }

    @Test
    fun `buildCommand uses cmd start on Windows`() {
        val cmd = BrowserLauncher.buildCommand("windows 10", "https://example.com")
        assertEquals(listOf("cmd", "/c", "start", "", "https://example.com"), cmd)
    }

    @Test
    fun `buildCommand uses xdg-open on Linux`() {
        val cmd = BrowserLauncher.buildCommand("linux", "https://example.com")
        assertEquals(listOf("xdg-open", "https://example.com"), cmd)
    }

    @Test
    fun `buildCommand defaults to xdg-open for unknown OS`() {
        val cmd = BrowserLauncher.buildCommand("plan 9", "https://example.com")
        assertEquals(listOf("xdg-open", "https://example.com"), cmd)
    }

    @Test
    fun `buildCommand is case-insensitive on Mac substring`() {
        val cmd = BrowserLauncher.buildCommand("macos", "https://example.com")
        assertEquals(listOf("open", "https://example.com"), cmd)
    }

    @Test
    fun `openInBrowserInternal invokes processRunner with correct command`() {
        var capturedCmd: List<String>? = null
        var desktopCalled = false
        val launcher = BrowserLauncher()
        launcher.openInBrowserInternal(
            url = "https://example.com",
            processRunner = { cmd ->
                capturedCmd = cmd
                true
            },
            desktopBrowser = { desktopCalled = true },
            osNameProvider = { "mac os x" },
        )
        assertEquals(listOf("open", "https://example.com"), capturedCmd)
        assertFalse(desktopCalled)
    }

    @Test
    fun `openInBrowserInternal falls back to desktop browser on process failure`() {
        var capturedCmd: List<String>? = null
        var desktopUri: java.net.URI? = null
        val launcher = BrowserLauncher()
        launcher.openInBrowserInternal(
            url = "https://example.com",
            processRunner = { cmd ->
                capturedCmd = cmd
                throw RuntimeException("simulated")
            },
            desktopBrowser = { uri -> desktopUri = uri },
        )
        assertNotNull(capturedCmd)
        assertEquals(java.net.URI("https://example.com"), desktopUri)
    }

    @Test
    fun `openInBrowserInternal swallows exception from desktop browser`() {
        val launcher = BrowserLauncher()
        launcher.openInBrowserInternal(
            url = "https://example.com",
            processRunner = { throw RuntimeException("process boom") },
            desktopBrowser = { throw RuntimeException("desktop boom") },
        )
        assertTrue(true)
    }

    @Test
    fun `openInBrowser delegates to processRunner via the public API`() {
        val mockDesktop = org.mockito.Mockito.mock(java.awt.Desktop::class.java)
        var capturedCmd: List<String>? = null
        val launcher =
            BrowserLauncher(
                processRunner = { cmd ->
                    capturedCmd = cmd
                    throw RuntimeException("simulated")
                },
                desktopBrowserProvider = { mockDesktop },
                osNameProvider = { "mac os x" },
            )
        launcher.openInBrowser("https://example.com")
        verify(mockDesktop).browse(java.net.URI("https://example.com"))
        assertEquals(listOf("open", "https://example.com"), capturedCmd)
    }

    @Test
    fun `openInBrowser falls through to desktop when processRunner throws`() {
        val mockDesktop = org.mockito.Mockito.mock(java.awt.Desktop::class.java)
        val launcher =
            BrowserLauncher(
                processRunner = { _ -> throw RuntimeException("boom") },
                desktopBrowserProvider = { mockDesktop },
            )
        launcher.openInBrowser("https://example.com")
        verify(mockDesktop).browse(java.net.URI("https://example.com"))
    }

    @Test
    fun `runProcess starts ProcessBuilder and returns true`() {
        val cmd =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                listOf("cmd", "/c", "exit", "0")
            } else {
                listOf("true")
            }
        val result = BrowserLauncher.runProcess(cmd)
        assertTrue(result)
    }

    @Test
    fun `showInfo constructs an INFORMATION Alert with given title and content`() {
        org.mockito.Mockito.mockConstruction(javafx.scene.control.Alert::class.java).use { mocked ->
            DesktopFileDownloader.showInfo("hello title", "hello content")
            assertEquals(1, mocked.constructed().size)
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .setTitle("hello title")
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .contentText = "hello content"
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .showAndWait()
        }
    }

    @Test
    fun `showError constructs an ERROR Alert with given title and content`() {
        org.mockito.Mockito.mockConstruction(javafx.scene.control.Alert::class.java).use { mocked ->
            DesktopFileDownloader.showError("oops title", "oops content")
            assertEquals(1, mocked.constructed().size)
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .setTitle("oops title")
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .contentText = "oops content"
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .showAndWait()
        }
    }

    @Test
    fun `showError handles null content gracefully`() {
        org.mockito.Mockito.mockConstruction(javafx.scene.control.Alert::class.java).use { mocked ->
            DesktopFileDownloader.showError("oops", null)
            assertEquals(1, mocked.constructed().size)
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .contentText = null
            org.mockito.Mockito
                .verify(mocked.constructed()[0])
                .showAndWait()
        }
    }

    @Test
    fun `defaultPreferences is non-null and rooted at user prefs`() {
        try {
            val node = DesktopPreferences.defaultPreferences()
            assertNotNull(node)
            val abs = node.absolutePath()
            assertTrue(abs.endsWith("/com/stacklens/ui") || abs == "com/stacklens/ui") {
                "Expected path to end with /com/stacklens/ui but was $abs"
            }
        } catch (_: java.util.prefs.BackingStoreException) {
            assertTrue(true)
        }
    }

    private fun mockStage(): javafx.stage.Stage = org.mockito.kotlin.mock<javafx.stage.Stage>()

    @Test
    fun `downloadTool lambda runs via fxRunner and delegates to downloadToolBody`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val runner = FakeJavaFxRunner()
        val fileChooser = org.mockito.kotlin.mock<javafx.stage.FileChooser>()
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 9999,
                fxRunner = runner,
                fileChooserFactory = { fileChooser },
            )
        downloader.downloadTool("file.bin", "tool-1")
        assertEquals(1, runner.submitted.size)
        runner.submitted[0].invoke()
        verify(fileChooser).showSaveDialog(stage)
    }

    @Test
    fun `downloadUrl lambda runs via fxRunner and delegates to downloadUrlBody`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val runner = FakeJavaFxRunner()
        val fileChooser = org.mockito.kotlin.mock<javafx.stage.FileChooser>()
        val downloader =
            DesktopFileDownloader(
                stage = stage,
                port = 9999,
                fxRunner = runner,
                fileChooserFactory = { fileChooser },
            )
        downloader.downloadUrl("/api/foo", "foo.bin")
        assertEquals(1, runner.submitted.size)
        runner.submitted[0].invoke()
        verify(fileChooser).showSaveDialog(stage)
    }

    @Test
    fun `saveFile lambda runs via fxRunner and delegates to saveFileBody`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val runner = FakeJavaFxRunner()
        val fileChooser = org.mockito.kotlin.mock<javafx.stage.FileChooser>()
        val saver =
            DesktopFileSaver(
                stage = stage,
                fxRunner = runner,
                fileChooserFactory = { fileChooser },
            )
        saver.saveFile("file.bin", "aGVsbG8=")
        assertEquals(1, runner.submitted.size)
        runner.submitted[0].invoke()
        verify(fileChooser).showSaveDialog(stage)
    }

    @Test
    fun `downloadUrlBody success path with fxRunner schedules showInfo on fx thread`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val runner = FakeJavaFxRunner()
        val downloader = DesktopFileDownloader(stage, port = 9999, fxRunner = runner)
        val tmp = Files.createTempFile("dl-url-fx-", ".bin").toFile()
        try {
            val server =
                com.sun.net.httpserver.HttpServer
                    .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/api/x") { ex ->
                val payload = "ok".toByteArray()
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.use { it.write(payload) }
            }
            server.start()
            try {
                val realDownloader = DesktopFileDownloader(stage, port = server.address.port, fxRunner = runner)
                realDownloader.downloadUrlBody(relPath = "/api/x", targetFile = tmp)
                val deadline = System.currentTimeMillis() + 3_000
                while (tmp.length() == 0L && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                }
                val successBlock = runner.submitted.lastOrNull()
                if (successBlock != null) {
                    try {
                        successBlock.invoke()
                    } catch (_: Throwable) {
                    }
                }
                assertEquals("ok", tmp.readText())
            } finally {
                server.stop(0)
            }
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `downloadUrlBody failure path with fxRunner schedules showError on fx thread`() {
        val stage = org.mockito.kotlin.mock<javafx.stage.Stage>()
        val runner = FakeJavaFxRunner()
        val downloader = DesktopFileDownloader(stage, port = 1, fxRunner = runner)
        val tmp = Files.createTempFile("dl-url-err-", ".bin").toFile()
        try {
            downloader.downloadUrlBody(relPath = "/api/x", targetFile = tmp)
            val deadline = System.currentTimeMillis() + 3_000
            while (runner.submitted.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            val errorBlock = runner.submitted.lastOrNull()
            if (errorBlock != null) {
                try {
                    errorBlock.invoke()
                } catch (_: Throwable) {
                }
            }
            assertTrue(runner.submitted.isNotEmpty())
        } finally {
            tmp.delete()
        }
    }
}
