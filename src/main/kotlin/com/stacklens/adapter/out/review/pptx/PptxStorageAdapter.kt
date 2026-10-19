package com.stacklens.adapter.out.review.pptx

import com.stacklens.application.port.out.PptxStoragePort
import com.stacklens.domain.util.quietly
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Component
class PptxStorageAdapter : PptxStoragePort {
    private val baseDir: File =
        run {
            val configured =
                System.getProperty("stacklens.data-dir")
                    ?: System.getenv("STACKLENS_DATA_DIR")
            if (configured != null && configured.isNotBlank()) {
                File(configured, "review-analyse")
            } else {
                File("data/review-analyse")
            }
        }

    fun uploadsDir(analysisId: Long): File {
        val dir = File(baseDir, "uploads/$analysisId")
        dir.mkdirs()
        return dir
    }

    fun resultsDir(analysisId: Long): File {
        val dir = File(baseDir, "results/$analysisId")
        dir.mkdirs()
        return dir
    }

    override fun saveUpload(
        analysisId: Long,
        originalFilename: String,
        bytes: ByteArray,
    ): File {
        val safe = sanitize(originalFilename)
        val target = File(uploadsDir(analysisId), "original-$safe")
        Files.write(target.toPath(), bytes)
        return target
    }

    override fun importFromServer(
        analysisId: Long,
        sourcePath: String,
    ): File {
        val src = File(sourcePath).canonicalFile
        require(src.exists() && src.isFile) { "Source PPTX not found: $sourcePath" }
        val target = File(uploadsDir(analysisId), "server-${src.name}")
        Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    override fun saveHtml(
        analysisId: Long,
        html: String,
    ): String {
        val target = File(resultsDir(analysisId), "filled.html")
        Files.writeString(target.toPath(), html)
        return target.absolutePath
    }

    override fun readUploadBytes(file: File): ByteArray = file.readBytes()

    override fun cleanup(analysisId: Long) {
        quietly { uploadsDir(analysisId).deleteRecursively() }
        quietly { resultsDir(analysisId).deleteRecursively() }
    }

    override fun findUploadedPptx(
        analysisId: Long,
        expectedName: String?,
    ): File? =
        uploadsDir(analysisId)
            .listFiles()
            ?.let { files ->
                when {
                    expectedName != null ->
                        files.firstOrNull {
                            it.isFile &&
                                it.length() > 0 &&
                                it.name.endsWith(expectedName, ignoreCase = true)
                        }
                    else ->
                        files.firstOrNull {
                            it.isFile &&
                                it.length() > 0 &&
                                it.extension.lowercase() in PptxFormats.EXTENSIONS
                        }
                }
            }

    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}
