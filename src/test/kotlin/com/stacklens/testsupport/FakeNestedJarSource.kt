package com.stacklens.testsupport

import com.stacklens.adapter.out.compiler.NestedJarSource
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

class FakeNestedJarSource(
    private val payloads: Map<String, ByteArray> = emptyMap(),
) : NestedJarSource {
    val requestedUrls: MutableList<String> = mutableListOf()

    override fun open(nestedJarUrl: String): InputStream {
        requestedUrls += nestedJarUrl
        val payload = payloads[nestedJarUrl] ?: throw FileNotFoundException(nestedJarUrl)
        return ByteArrayInputStream(payload)
    }
}
