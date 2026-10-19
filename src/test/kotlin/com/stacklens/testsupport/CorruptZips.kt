package com.stacklens.testsupport

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CorruptZips {
    fun unreadableEntry(
        entryName: String = "manifest.json",
        payload: String = "{}",
    ): ByteArray {
        val bytes =
            ByteArrayOutputStream()
                .also { out ->
                    ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(payload.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }.toByteArray()
        bytes[8] = 9
        bytes[9] = 0
        return bytes
    }

    fun brokenCentralDirectory(): ByteArray {
        val bytes =
            ByteArrayOutputStream()
                .also { out ->
                    ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(ZipEntry("a.txt"))
                        zip.write("x".toByteArray())
                        zip.closeEntry()
                    }
                }.toByteArray()
        val marker =
            byteArrayOf(0x50, 0x4B, 0x01, 0x02).let { signature ->
                (0..bytes.size - signature.size).firstOrNull { index -> bytes.copyOfRange(index, index + signature.size).contentEquals(signature) }
            } ?: return bytes
        bytes[marker + 2] = 0x7F
        return bytes
    }

    fun zipWithCorruptedData(
        entries: Map<String, ByteArray>,
        corruptEntry: String,
    ): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val bytes = buffer.toByteArray()
        val name = corruptEntry.toByteArray(Charsets.UTF_8)
        var offset = 0
        while (offset + 30 <= bytes.size) {
            if (bytes[offset] == 0x50.toByte() && bytes[offset + 1] == 0x4B.toByte() && bytes[offset + 2] == 3.toByte() && bytes[offset + 3] == 4.toByte()) {
                val nameLength = (bytes[offset + 26].toInt() and 0xFF) or ((bytes[offset + 27].toInt() and 0xFF) shl 8)
                val extraLength = (bytes[offset + 28].toInt() and 0xFF) or ((bytes[offset + 29].toInt() and 0xFF) shl 8)
                val entryName = bytes.copyOfRange(offset + 30, offset + 30 + nameLength).toString(Charsets.UTF_8)
                if (entryName == corruptEntry) {
                    val dataStart = offset + 30 + nameLength + extraLength
                    repeat(minOf(64, bytes.size - dataStart - 1)) { index ->
                        bytes[dataStart + index] = (bytes[dataStart + index].toInt() xor 0xFF).toByte()
                    }
                    return bytes
                }
            }
            offset++
        }
        throw IllegalStateException("no local header for $corruptEntry")
    }
}
