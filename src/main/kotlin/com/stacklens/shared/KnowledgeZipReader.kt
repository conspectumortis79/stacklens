package com.stacklens.shared

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

internal object KnowledgeZipReader {
    const val VECTORS_PREFIX: String = "vectors/"

    fun findEntry(
        payload: ByteArray,
        entryName: String,
    ): ByteArray? = entries(payload, names = setOf(entryName))[entryName]

    fun entries(
        payload: ByteArray,
        names: Set<String> = emptySet(),
        prefix: String? = null,
    ): Map<String, ByteArray> {
        val found = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name in names || (prefix != null && name.startsWith(prefix))) {
                    found[name] = zip.readCappedBytes()
                }
                entry = zip.nextEntry
            }
        }
        return found
    }

    fun readEntryAsString(
        payload: ByteArray,
        entryName: String,
    ): String? = findEntry(payload, entryName)?.toString(Charsets.UTF_8)
}
