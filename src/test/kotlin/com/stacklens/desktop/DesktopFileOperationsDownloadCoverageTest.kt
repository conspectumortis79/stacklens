package com.stacklens.desktop

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

class DesktopFileOperationsDownloadCoverageTest {
    private class BreakingStreamHandler : URLStreamHandler() {
        override fun openConnection(u: URL): URLConnection =
            object : URLConnection(u) {
                override fun connect() {}

                override fun getInputStream(): InputStream =
                    object : InputStream() {
                        override fun read(): Int = throw IOException("the connection dropped")

                        override fun read(
                            b: ByteArray,
                            off: Int,
                            len: Int,
                        ): Int = throw IOException("the connection dropped")
                    }
            }
    }

    @Suppress("DEPRECATION")
    private fun breakingUrl(): URL = URL("http", "localhost", 8099, "/tool-export", BreakingStreamHandler())

    @Test
    fun `a stream that breaks while copying unwinds both use blocks`(
        @TempDir dir: File,
    ) {
        val target = File(dir, "tool.jar")

        assertThrows(IOException::class.java) { DesktopFileDownloader.downloadToFile(breakingUrl(), target) }
    }

    @Test
    fun `a target that cannot be opened unwinds the input use block`(
        @TempDir dir: File,
    ) {
        val directoryAsTarget = File(dir, "somewhere").apply { mkdir() }

        assertThrows(IOException::class.java) { DesktopFileDownloader.downloadToFile(breakingUrl(), directoryAsTarget) }
    }
}
