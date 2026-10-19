package com.stacklens.adapter.out.shared

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.stacklens.application.port.out.JsonTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JacksonJsonCodecAdapterNodeKindTest {
    private val adapter = JacksonJsonCodecAdapter(ObjectMapper())

    @Test
    fun `a node that is neither container nor scalar degrades to its text form`() {
        val missing = JsonNodeFactory.instance.missingNode()

        val tree =
            with(adapter) {
                missing.toJsonTree()
            }

        assertEquals(JsonTree.TextNode(""), tree)
    }

    @Test
    fun `a binary node degrades to its text form too`() {
        val binary = JsonNodeFactory.instance.binaryNode(byteArrayOf(1, 2, 3))

        val tree =
            with(adapter) {
                binary.toJsonTree()
            }

        assertEquals(JsonTree.TextNode(binary.asText()), tree)
    }
}
