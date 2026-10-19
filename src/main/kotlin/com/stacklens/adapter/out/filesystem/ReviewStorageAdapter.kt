
package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.storage.ReviewStoragePort
import java.io.File

class ReviewStorageAdapter : ReviewStoragePort {
    private val baseDir = "data/review-analyse"

    override fun readPromptOverride(lang: String): String? {
        val file = File("$baseDir/prompt_$lang.txt")
        return if (file.exists()) file.readText() else null
    }

    override fun savePromptOverride(
        lang: String,
        content: String,
    ) {
        val dir = File(baseDir)
        dir.mkdirs()
        File(dir, "prompt_$lang.txt").writeText(content)
    }

    override fun deletePromptOverride(lang: String) {
        File("$baseDir/prompt_$lang.txt").delete()
    }
}
