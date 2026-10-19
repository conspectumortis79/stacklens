package com.stacklens.application.service.loadtest

import com.stacklens.application.port.out.file.FileReadWriteDeletePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class K6SummaryFileReaderTest {
    private val fileSystem = mock<FileReadWriteDeletePort>()
    private val reader = K6SummaryFileReader(fileSystem)

    @Test
    fun `reads and deletes a populated summary file`() {
        val file = File("summary.json")
        whenever(fileSystem.exists(file)).thenReturn(true)
        whenever(fileSystem.size(file)).thenReturn(10L)
        whenever(fileSystem.readText(file)).thenReturn("""{"metrics":{}}""")
        whenever(fileSystem.deleteFile(file)).thenReturn(true)

        assertEquals("""{"metrics":{}}""", reader.readAndDelete(file))
        verify(fileSystem).deleteFile(file)
    }

    @Test
    fun `returns null for an absent or too small summary file`() {
        val missing = File("missing.json")
        whenever(fileSystem.exists(missing)).thenReturn(false)

        assertNull(reader.readAndDelete(missing))

        val tiny = File("tiny.json")
        whenever(fileSystem.exists(tiny)).thenReturn(true)
        whenever(fileSystem.size(tiny)).thenReturn(2L)

        assertNull(reader.readAndDelete(tiny))
    }

    @Test
    fun `returns null when the summary file cannot be read but still deletes it`() {
        val file = File("broken.json")
        whenever(fileSystem.exists(file)).thenReturn(true)
        whenever(fileSystem.size(file)).thenReturn(20L)
        whenever(fileSystem.readText(file)).thenThrow(RuntimeException("io"))

        assertNull(reader.readAndDelete(file))
        verify(fileSystem).deleteFile(file)
    }
}
