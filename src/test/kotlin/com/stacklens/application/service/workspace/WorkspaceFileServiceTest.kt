package com.stacklens.application.service.workspace

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileCopyRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import com.stacklens.application.port.out.file.FileListing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.nio.file.Files

class WorkspaceFileServiceTest {
    private val adapter = JavaFileSystemAdapter()
    private val service = WorkspaceFileService(adapter, adapter, adapter, adapter)

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `saveText writes the content and returns the absolute path`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "script.js",
                    content = "console.log(1)",
                ),
            )

        val expected = File(tempDir, "script.js")
        assertEquals(WorkspaceWriteResult.Saved(expected.absolutePath), result)
        assertEquals("console.log(1)", expected.readText())
    }

    @Test
    fun `saveText canonicalizes the directory when requested`() {
        val real = File(tempDir, "real").apply { mkdirs() }
        val link = File(tempDir, "link")
        Files.createSymbolicLink(link.toPath(), real.toPath())

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = link.absolutePath,
                    filename = "script.js",
                    content = "x",
                    canonicalizeDirectory = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(real.canonicalFile, "script.js").absolutePath), result)
    }

    @Test
    fun `saveText rejects a directory that cannot be canonicalized`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = "bad\u0000dir",
                    filename = "script.js",
                    content = "x",
                    canonicalizeDirectory = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND), result)
    }

    @Test
    fun `saveText rejects a missing directory`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = File(tempDir, "missing").absolutePath,
                    filename = "script.js",
                    content = "x",
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND), result)
    }

    @Test
    fun `saveText rejects a path that is not a directory`() {
        val file = File(tempDir, "file.txt").apply { writeText("x") }

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = file.absolutePath,
                    filename = "script.js",
                    content = "x",
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND), result)
    }

    @Test
    fun `saveText uses the fallback filename when the filename is empty`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "",
                    content = "x",
                    fallbackFilename = "fallback.js",
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(tempDir, "fallback.js").absolutePath), result)
    }

    @Test
    fun `saveText fails when neither filename nor fallback is present`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "",
                    content = "x",
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED), result)
    }

    @Test
    fun `saveText sanitizes unsafe filename characters`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "my config!@#.js",
                    content = "x",
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(tempDir, "my_config___.js").absolutePath), result)
    }

    @Test
    fun `saveText appends the json extension when enforced`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "config",
                    content = "{}",
                    enforceJsonExtension = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(tempDir, "config.json").absolutePath), result)
    }

    @Test
    fun `saveText keeps an existing json extension`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "config.json",
                    content = "{}",
                    enforceJsonExtension = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(tempDir, "config.json").absolutePath), result)
    }

    @Test
    fun `saveText rejects a target outside the allowed root`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        val outside = File(tempDir, "outside").apply { mkdirs() }

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = outside.absolutePath,
                    filename = "script.js",
                    content = "x",
                    allowedRoot = root.absolutePath,
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT), result)
    }

    @Test
    fun `saveText rejects a target whose canonical file cannot be resolved`() {
        val attributes = mock<FileAttributes>()
        whenever(attributes.canonicalFile(any())).thenAnswer { invocation ->
            val file = invocation.getArgument(0) as File
            if (file.name == "boom.js") throw IOException("broken link") else file.canonicalFile
        }
        whenever(attributes.exists(any())).thenReturn(true)
        whenever(attributes.isDirectory(any())).thenReturn(true)
        val guarded = WorkspaceFileService(attributes, adapter, adapter, adapter)

        val result =
            guarded.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "boom.js",
                    content = "x",
                    allowedRoot = tempDir.absolutePath,
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT), result)
    }

    @Test
    fun `saveText rejects an unresolvable allowed root`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "script.js",
                    content = "x",
                    allowedRoot = "bad\u0000root",
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT), result)
    }

    @Test
    fun `saveText saves a target inside the allowed root`() {
        val root = File(tempDir, "root").apply { mkdirs() }
        val nested = File(root, "nested").apply { mkdirs() }

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = nested.absolutePath,
                    filename = "config.json",
                    content = "{}",
                    allowedRoot = root.absolutePath,
                ),
            )

        val expected = File(nested.canonicalFile, "config.json")
        assertEquals(WorkspaceWriteResult.Saved(expected.absolutePath), result)
        assertEquals(true, expected.exists())
    }

    @Test
    fun `saveText treats a target equal to the allowed root as inside the root`() {
        val root = File(tempDir, "root").apply { mkdirs() }

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "root",
                    content = "x",
                    allowedRoot = root.absolutePath,
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED), result)
    }

    @Test
    fun `saveText sanitizes a blank filename when no fallback is configured`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "   ",
                    content = "x",
                ),
            )

        val expected = File(tempDir, "___")
        assertEquals(WorkspaceWriteResult.Saved(expected.absolutePath), result)
        assertEquals(true, expected.exists())
    }

    @Test
    fun `saveText requires an existing file`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "missing.txt",
                    content = "x",
                    requireExistingFile = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE), result)
    }

    @Test
    fun `saveText requires the target to be a file`() {
        File(tempDir, "directory.txt").mkdirs()

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "directory.txt",
                    content = "x",
                    requireExistingFile = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE), result)
    }

    @Test
    fun `saveText overwrites an existing file when requested`() {
        File(tempDir, "existing.txt").writeText("old")

        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "existing.txt",
                    content = "new",
                    requireExistingFile = true,
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(tempDir, "existing.txt").absolutePath), result)
        assertEquals("new", File(tempDir, "existing.txt").readText())
    }

    @Test
    fun `saveText rejects a disallowed extension`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "script.js",
                    content = "x",
                    allowedExtensions = setOf("json"),
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE), result)
    }

    @Test
    fun `saveText matches the extension case insensitively`() {
        val result =
            service.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "config.JSON",
                    content = "{}",
                    allowedExtensions = setOf("json"),
                ),
            )

        assertEquals(WorkspaceWriteResult.Saved(File(tempDir, "config.JSON").absolutePath), result)
    }

    @Test
    fun `saveText rejects when the store throws`() {
        val failing = WorkspaceFileService(adapter, ThrowingContentStore(), adapter, adapter)

        val result =
            failing.saveText(
                TextFileSaveRequest(
                    directory = tempDir.absolutePath,
                    filename = "script.js",
                    content = "x",
                ),
            )

        assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED), result)
    }

    @Test
    fun `probeAccess reports missing paths`() {
        val missing = File(tempDir, "missing")

        val view = service.probeAccess(missing.absolutePath)

        assertEquals(missing.canonicalFile.path, view.path)
        assertEquals(false, view.exists)
        assertEquals(false, view.isDirectory)
        assertEquals(false, view.canRead)
        assertEquals(false, view.canWrite)
        assertEquals(false, view.probeOk)
        assertEquals(null, view.probeError)
    }

    @Test
    fun `probeAccess reports an unresolvable path`() {
        val path = "bad\u0000path"

        val view = service.probeAccess(path)

        assertEquals(path, view.path)
        assertEquals(false, view.exists)
        assertEquals(false, view.probeOk)
        assertNotNull(view.probeError)
    }

    @Test
    fun `probeAccess reports missing write permission`() {
        val attributes = mock<FileAttributes>()
        whenever(attributes.canonicalFile(any())).thenAnswer { (it.getArgument(0) as File).canonicalFile }
        whenever(attributes.exists(any())).thenReturn(true)
        whenever(attributes.isDirectory(any())).thenReturn(true)
        whenever(attributes.canRead(any())).thenReturn(true)
        whenever(attributes.canWrite(any())).thenReturn(false)
        val readOnlyService = WorkspaceFileService(attributes, adapter, adapter, adapter)

        val view = readOnlyService.probeAccess(tempDir.absolutePath)

        assertEquals(tempDir.canonicalFile.path, view.path)
        assertEquals(true, view.exists)
        assertEquals(true, view.isDirectory)
        assertEquals(true, view.canRead)
        assertEquals(false, view.canWrite)
        assertEquals(false, view.probeOk)
        assertEquals(null, view.probeError)
    }

    @Test
    fun `probeAccess passes the write probe and removes the temporary directory`() {
        val view = service.probeAccess(tempDir.absolutePath)

        assertEquals(tempDir.canonicalFile.path, view.path)
        assertEquals(true, view.exists)
        assertEquals(true, view.isDirectory)
        assertEquals(true, view.canRead)
        assertEquals(true, view.canWrite)
        assertEquals(true, view.probeOk)
        assertEquals(null, view.probeError)
        assertEquals(false, File(tempDir, ".stacklens").exists())
    }

    @Test
    fun `probeAccess keeps a pre-existing probe directory`() {
        val stacklensDir = File(tempDir, ".stacklens").apply { mkdirs() }
        File(stacklensDir, "keep.txt").writeText("keep")

        val view = service.probeAccess(tempDir.absolutePath)

        assertEquals(true, view.probeOk)
        assertEquals(true, stacklensDir.exists())
        assertEquals(listOf("keep.txt"), stacklensDir.list()?.toList())
    }

    @Test
    fun `probeAccess reports a failed probe`() {
        val failing = WorkspaceFileService(adapter, ThrowingContentStore(), adapter, adapter)

        val view = failing.probeAccess(tempDir.absolutePath)

        assertEquals(false, view.probeOk)
        assertEquals("write failed", view.probeError)
        assertEquals(false, File(tempDir, ".stacklens").exists())
    }

    @Test
    fun `probeAccess keeps foreign files when the probe fails`() {
        val store =
            ThrowingContentStore { file ->
                file.parentFile?.mkdirs()
                File(file.parentFile, "keep.txt").writeText("keep")
            }
        val failing = WorkspaceFileService(adapter, store, adapter, adapter)

        val view = failing.probeAccess(tempDir.absolutePath)

        assertEquals(false, view.probeOk)
        assertEquals(true, File(File(tempDir, ".stacklens"), "keep.txt").exists())
    }

    @Test
    fun `homeDirectory exposes the configured user home`() {
        assertEquals(System.getProperty("user.home"), service.homeDirectory())
    }

    @Test
    fun `readBytes returns null for missing paths and directories`() {
        assertNull(service.readBytes(File(tempDir, "missing.txt").absolutePath))
        assertNull(service.readBytes(tempDir.absolutePath))
    }

    @Test
    fun `readBytes reads an existing file`() {
        val file = File(tempDir, "data.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(listOf<Byte>(1, 2, 3), service.readBytes(file.absolutePath)!!.toList())
    }

    @Test
    fun `readText returns null for missing paths and directories`() {
        assertNull(service.readText(File(tempDir, "missing.txt").absolutePath))
        assertNull(service.readText(tempDir.absolutePath))
    }

    @Test
    fun `readText reads an existing file`() {
        val file = File(tempDir, "data.txt").apply { writeText("hello") }
        assertEquals("hello", service.readText(file.absolutePath))
    }

    @Test
    fun `fileInfo returns null for a missing path`() {
        assertNull(service.fileInfo(File(tempDir, "missing.txt").absolutePath))
    }

    @Test
    fun `fileInfo returns null when the attributes cannot be inspected`() {
        val attributes = mock<FileAttributes>()
        whenever(attributes.exists(any())).thenThrow(IllegalStateException("stat failed"))
        val broken = WorkspaceFileService(attributes, adapter, adapter, adapter)
        assertNull(broken.fileInfo("anything"))
    }

    @Test
    fun `fileInfo reports file and directory attributes`() {
        val file = File(tempDir, "data.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val info = service.fileInfo(file.absolutePath)!!
        assertEquals("data.bin", info.name)
        assertEquals(file.absolutePath, info.path)
        assertEquals(3L, info.size)
        assertTrue(info.isFile)
        assertFalse(info.isDirectory)

        val dir = File(tempDir, "sub").apply { mkdirs() }
        val dirInfo = service.fileInfo(dir.absolutePath)!!
        assertTrue(dirInfo.isDirectory)
        assertFalse(dirInfo.isFile)
    }

    @Test
    fun `browseDirectory lists visible entries and reports the parent path`() {
        File(tempDir, "sub").mkdirs()
        File(tempDir, "a.png").writeText("a")
        File(tempDir, ".hidden.png").writeText("h")

        val listing = service.browseDirectory(tempDir.absolutePath)!!

        assertEquals(tempDir.absolutePath, listing.path)
        assertEquals(tempDir.parentFile.absolutePath, listing.parentPath)
        assertEquals(setOf("sub", "a.png"), listing.entries.map { it.name }.toSet())
        val sub = listing.entries.first { it.name == "sub" }
        assertTrue(sub.isDirectory)
        assertFalse(sub.isFile)
        val png = listing.entries.first { it.name == "a.png" }
        assertEquals(1L, png.size)
        assertTrue(png.isFile)
    }

    @Test
    fun `browseDirectory falls back to the home directory for null and blank paths`() {
        assertEquals(System.getProperty("user.home"), service.browseDirectory(null)!!.path)
        assertEquals(System.getProperty("user.home"), service.browseDirectory("   ")!!.path)
    }

    @Test
    fun `browseDirectory returns null for missing paths and files`() {
        assertNull(service.browseDirectory(File(tempDir, "missing").absolutePath))
        val file = File(tempDir, "f.txt").apply { writeText("x") }
        assertNull(service.browseDirectory(file.absolutePath))
    }

    @Test
    fun `browseDirectory exposes a null parent for the filesystem root`() {
        val listing = service.browseDirectory("/")

        assertNotNull(listing)
        assertNull(listing!!.parentPath)
    }

    @Test
    fun `browseDirectory skips entries whose attributes fail`() {
        val attributes = mock<FileAttributes>()
        whenever(attributes.homeDirectory()).thenReturn(tempDir)
        whenever(attributes.exists(any())).thenReturn(true)
        whenever(attributes.isDirectory(any())).thenReturn(true, false)
        whenever(attributes.isFile(any())).thenThrow(IllegalStateException("stat failed"))
        val listing = mock<FileListing>()
        whenever(listing.listEntries(any())).thenReturn(listOf(File(tempDir, "broken.png")))
        val broken = WorkspaceFileService(attributes, adapter, adapter, listing)

        assertTrue(broken.browseDirectory(tempDir.absolutePath)!!.entries.isEmpty())
    }

    @Test
    fun `copyFile copies a file into an existing directory`() {
        val source = File(tempDir, "source.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val dir = File(tempDir, "target").apply { mkdirs() }

        val result = service.copyFile(WorkspaceFileCopyRequest(source.absolutePath, dir.absolutePath, "copy.jar"))

        assertEquals(WorkspaceWriteResult.Saved(File(dir, "copy.jar").absolutePath), result)
        assertEquals(listOf<Byte>(1, 2, 3), File(dir, "copy.jar").readBytes().toList())
    }

    @Test
    fun `copyFile rejects missing or non-directory target paths`() {
        val source = File(tempDir, "source.jar").apply { writeBytes(byteArrayOf(1)) }
        assertEquals(
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND),
            service.copyFile(WorkspaceFileCopyRequest(source.absolutePath, File(tempDir, "missing").absolutePath, "copy.jar")),
        )
        assertEquals(
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND),
            service.copyFile(WorkspaceFileCopyRequest(source.absolutePath, source.absolutePath, "copy.jar")),
        )
    }

    @Test
    fun `copyFile rejects missing or non-file sources`() {
        assertEquals(
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
            service.copyFile(WorkspaceFileCopyRequest(File(tempDir, "missing.jar").absolutePath, tempDir.absolutePath, "copy.jar")),
        )
        val dir = File(tempDir, "src-dir").apply { mkdirs() }
        assertEquals(
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.TARGET_NOT_A_FILE),
            service.copyFile(WorkspaceFileCopyRequest(dir.absolutePath, tempDir.absolutePath, "copy.jar")),
        )
    }

    @Test
    fun `copyFile reports a write failure`() {
        val source = File(tempDir, "source.jar").apply { writeBytes(byteArrayOf(1)) }
        val failing = WorkspaceFileService(adapter, ThrowingContentStore(), adapter, adapter)

        assertEquals(
            WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED),
            failing.copyFile(WorkspaceFileCopyRequest(source.absolutePath, tempDir.absolutePath, "copy.jar")),
        )
    }

    @Test
    fun `write rejections expose every reason`() {
        assertEquals(5, WorkspaceWriteRejection.values().size)
        assertEquals(WorkspaceWriteRejection.WRITE_FAILED, WorkspaceWriteRejection.valueOf("WRITE_FAILED"))
    }

    private class ThrowingContentStore(
        private val beforeThrow: (File) -> Unit = {},
    ) : FileContentStore {
        override fun writeFile(
            file: File,
            content: String,
        ) {
            beforeThrow(file)
            throw IOException("write failed")
        }

        override fun writeBytes(
            file: File,
            bytes: ByteArray,
        ) {
            beforeThrow(file)
            throw IOException("write failed")
        }

        override fun readText(file: File): String = throw UnsupportedOperationException()

        override fun readBytes(file: File): ByteArray = throw UnsupportedOperationException()
    }
}
