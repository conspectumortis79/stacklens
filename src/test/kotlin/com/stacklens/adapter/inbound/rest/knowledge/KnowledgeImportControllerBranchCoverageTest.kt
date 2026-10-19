package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundlePreview
import com.stacklens.application.port.inbound.knowledge.KnowledgeExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeLocalFileImportUseCase
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import com.stacklens.testsupport.FakeUpload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class KnowledgeImportControllerBranchCoverageTest {
    private val export = FakeExport()

    private val import = FakeImport()

    private val bundleExport = FakeBundleExport()

    private val bundleImport = FakeBundleImport()

    private val localFileImport = FakeLocalFileImport()

    private val controller = KnowledgeImportController(export, import, bundleExport, bundleImport, localFileImport)

    private fun singleArchive(): ByteArray = "SLKNpayload".toByteArray()

    private fun uploadWith(content: ByteArray): FakeUpload = FakeUpload(fieldName = "file", fileName = "kb.slknowledge", mime = "application/octet-stream", content = content)

    @Test
    fun `export base applies the default export options`() {
        val payload = controller.exportBase(3L, ExportBaseRequest(password = "pw")).body!!
        assertEquals("SLKNpayload".toByteArray().size, payload.size)
        assertEquals(true, export.options?.includeVectors)
        assertEquals(true, export.options?.includeSourceDefinitions)
        assertEquals(true, export.options?.includeEmbeddingProfile)

        controller.exportBase(
            4L,
            ExportBaseRequest(
                password = "pw",
                includeVectors = false,
                includeSourceDefinitions = false,
                includeEmbeddingProfile = false,
            ),
        )
        assertEquals(false, export.options?.includeSourceDefinitions)
        assertEquals(false, export.options?.includeEmbeddingProfile)
        assertEquals(false, export.options?.includeVectors)
    }

    @Test
    fun `export all returns the bundle payload`() {
        assertEquals("bundle", String(controller.exportAll(ExportAllRequest(password = "pw")).body!!))
        assertEquals("pw", bundleExport.password)
    }

    @Test
    fun `bundle import delegates to the local file use case`() {
        localFileImport.bundleResults = listOf(result(1L, "alpha"), result(2L, "beta"))
        val results = controller.importBundleLocal(LocalBundleImportRequest("/tmp/a.slbundle", "  pw  ", "merge"))
        assertEquals(2, results.size)
        val recorded = localFileImport.bundleImports.single()
        assertEquals("/tmp/a.slbundle", recorded.first)
        assertEquals("  pw  ", recorded.second)
        assertEquals(KnowledgeImportMode.MERGE, recorded.third)
    }

    @Test
    fun `local import maps the aggregated result`() {
        localFileImport.aggregatedResults = listOf(result(3L, "alpha, beta"))
        val result = controller.importLocal(LocalImportRequest("/tmp/a.slbundle", null, "replace"))
        assertEquals(3L, result.knowledgeBaseId)
        assertEquals("alpha, beta", result.knowledgeBaseName)
        val recorded = localFileImport.aggregatedImports.single()
        assertEquals("/tmp/a.slbundle", recorded.first)
        assertEquals(null, recorded.second)
        assertEquals(KnowledgeImportMode.REPLACE, recorded.third)
    }

    @Test
    fun `local imports surface use case rejections`() {
        localFileImport.failure = IllegalArgumentException("Datei nicht gefunden: /tmp/missing.slknowledge")
        assertThrows(IllegalArgumentException::class.java) {
            controller.importLocal(LocalImportRequest("/tmp/missing.slknowledge", "pw", null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller.importBundleLocal(LocalBundleImportRequest("/tmp/missing.slknowledge", "pw", null))
        }
    }

    @Test
    fun `bundle upload import requires a password`() {
        bundleImport.results = listOf(result(1L, "alpha"), result(2L, "beta"))
        val results = controller.importBundle(uploadWith(singleArchive()), "  pw  ", "replace")
        assertEquals(2, results.size)
        assertEquals("  pw  ", bundleImport.imports.last().second)

        assertEquals(0, controller.inspectBundleImport(uploadWith(singleArchive())).entryCount)
    }

    @Test
    fun `inspect treats blank passwords as absent`() {
        controller.inspectImport(uploadWith(singleArchive()), "   ")
        assertEquals(null, export.inspectedPassword)

        controller.inspectImport(uploadWith(singleArchive()), "secret")
        assertEquals("secret", export.inspectedPassword)

        controller.inspectImport(uploadWith(singleArchive()), null)
        assertEquals(null, export.inspectedPassword)
    }

    @Test
    fun `import base forwards optional upload parts`() {
        controller.importBase(uploadWith(singleArchive()), "   ", null, null, null)
        val defaulted = import.requests.last()
        assertEquals(null, defaulted.password)
        assertEquals(KnowledgeImportMode.NEW_COPY, defaulted.mode)
        assertEquals(true, defaulted.includeVectors)
        assertEquals(null, defaulted.targetKnowledgeBaseId)

        controller.importBase(uploadWith(singleArchive()), "pw", "merge", 9L, "false")
        val explicit = import.requests.last()
        assertEquals("pw", explicit.password)
        assertEquals(KnowledgeImportMode.MERGE, explicit.mode)
        assertEquals(9L, explicit.targetKnowledgeBaseId)
        assertEquals(false, explicit.includeVectors)
    }

    private fun result(
        id: Long,
        name: String,
    ) = KnowledgeImportResult(id, name, 1, 2, 3, KnowledgeImportMode.NEW_COPY)

    private class FakeExport : KnowledgeExportUseCase {
        var options: KnowledgeExportOptions? = null

        var inspectedPassword: String? = "unset"

        override fun exportBase(
            knowledgeBaseId: Long,
            options: KnowledgeExportOptions,
            password: String,
        ): ByteArray {
            this.options = options
            return "SLKNpayload".toByteArray()
        }

        override fun inspect(
            archiveBytes: ByteArray,
            password: String?,
        ): KnowledgeImportPreview {
            inspectedPassword = password
            return KnowledgeImportPreview("kb", 1, 2, 3, true, null, null, null)
        }
    }

    private class FakeImport : KnowledgeImportUseCase {
        val requests: MutableList<KnowledgeImportRequest> = mutableListOf()

        var result: KnowledgeImportResult = KnowledgeImportResult(7L, "kb", 1, 2, 3, KnowledgeImportMode.NEW_COPY)

        override fun import(request: KnowledgeImportRequest): KnowledgeImportResult {
            requests += request
            return result
        }
    }

    private class FakeBundleExport : KnowledgeBundleExportUseCase {
        var password: String? = null

        override fun exportAll(
            options: KnowledgeExportOptions,
            password: String,
        ): ByteArray {
            this.password = password
            return "bundle".toByteArray()
        }
    }

    private class FakeBundleImport : KnowledgeBundleImportUseCase {
        val imports: MutableList<Triple<ByteArray, String, KnowledgeImportMode>> = mutableListOf()

        var results: List<KnowledgeImportResult> = listOf(KnowledgeImportResult(1L, "alpha", 1, 2, 3, KnowledgeImportMode.NEW_COPY))

        override fun inspect(bundleBytes: ByteArray): KnowledgeBundlePreview = KnowledgeBundlePreview(0, emptyList())

        override fun import(
            bundleBytes: ByteArray,
            password: String,
            mode: KnowledgeImportMode,
        ): List<KnowledgeImportResult> {
            imports += Triple(bundleBytes, password, mode)
            return results
        }
    }

    private class FakeLocalFileImport : KnowledgeLocalFileImportUseCase {
        val bundleImports: MutableList<Triple<String, String?, KnowledgeImportMode>> = mutableListOf()

        val aggregatedImports: MutableList<Triple<String, String?, KnowledgeImportMode>> = mutableListOf()

        var bundleResults: List<KnowledgeImportResult> = emptyList()

        var aggregatedResults: List<KnowledgeImportResult> = emptyList()

        var failure: IllegalArgumentException? = null

        override fun importFromLocalFile(
            path: String,
            password: String?,
            mode: KnowledgeImportMode,
        ): List<KnowledgeImportResult> {
            failure?.let { throw it }
            bundleImports += Triple(path, password, mode)
            return bundleResults
        }

        override fun importAggregatedFromLocalFile(
            path: String,
            password: String?,
            mode: KnowledgeImportMode,
        ): List<KnowledgeImportResult> {
            failure?.let { throw it }
            aggregatedImports += Triple(path, password, mode)
            return aggregatedResults
        }
    }
}
