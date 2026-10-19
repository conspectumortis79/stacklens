package com.stacklens.shared

import java.io.ByteArrayOutputStream
import java.io.InputStream

const val DEFAULT_MAX_CAPPED_BYTES: Int = 1_000_000_000

fun InputStream.readCappedBytes(maxSize: Int = DEFAULT_MAX_CAPPED_BYTES): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxSize) error("Archive exceeds maximum supported size")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
