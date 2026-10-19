package com.stacklens.adapter.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepoFileTextToolCallParserTest {
    @Test
    fun `parse returns an empty list for blank content`() {
        assertTrue(RepoFileTextToolCallParser.parse("").isEmpty())
    }

    @Test
    fun `parse returns an empty list for whitespace-only content`() {
        assertTrue(RepoFileTextToolCallParser.parse("   \n\t  ").isEmpty())
    }

    @Test
    fun `parse returns an empty list for content without any recognisable tool call`() {
        assertTrue(RepoFileTextToolCallParser.parse("Hello, this is a regular chat reply.").isEmpty())
    }

    @Test
    fun `parse extracts a single XML function call with one parameter`() {
        val content = "<function=write><parameter=path>/tmp/foo.txt</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val fn = results[0]["function"] as Map<*, *>
        assertEquals("write", fn["name"])
        val args = jacksonRead(fn["arguments"] as String)
        assertEquals("/tmp/foo.txt", args["path"])
    }

    @Test
    fun `parse extracts multiple XML function calls in document order`() {
        val content =
            "<function=write><parameter=path>/tmp/a.txt</parameter></function>" +
                " <function=read><parameter=path>/tmp/b.txt</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(2, results.size)
        assertEquals("write", (results[0]["function"] as Map<*, *>)["name"])
        assertEquals("read", (results[1]["function"] as Map<*, *>)["name"])
    }

    @Test
    fun `parse XML function call with no parameters yields an empty args object`() {
        val content = "<function=ping></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertTrue(args.isEmpty())
    }

    @Test
    fun `parse XML function call trims exactly one leading and one trailing newline from a parameter value`() {
        val content = "<function=write><parameter=content>\nhello\n</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals("hello", args["content"])
    }

    @Test
    fun `parse XML function call keeps internal newlines and indentation in the parameter value`() {
        val content = "<function=write><parameter=content>\n  line 1\n  line 2\n</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals("  line 1\n  line 2", args["content"])
    }

    @Test
    fun `parse extracts a fenced JSON tool_call block with name and arguments`() {
        val content =
            "```tool_call\n" +
                "{\"name\": \"write\", \"arguments\": {\"path\": \"/tmp/c.txt\"}}\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val fn = results[0]["function"] as Map<*, *>
        assertEquals("write", fn["name"])
        val args = jacksonRead(fn["arguments"] as String)
        assertEquals("/tmp/c.txt", args["path"])
    }

    @Test
    fun `parse fenced tool block (without the _call suffix) is also recognised`() {
        val content =
            "```tool\n" +
                "{\"name\": \"read\", \"arguments\": {\"path\": \"/tmp/d.txt\"}}\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        assertEquals("read", (results[0]["function"] as Map<*, *>)["name"])
    }

    @Test
    fun `parse fenced json block (no tool_call prefix) is also recognised`() {
        val content =
            "```json\n" +
                "{\"name\": \"delete\", \"arguments\": {\"path\": \"/tmp/e.txt\"}}\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        assertEquals("delete", (results[0]["function"] as Map<*, *>)["name"])
    }

    @Test
    fun `parse fenced tool_call with string arguments stores the raw JSON`() {
        val content =
            "```tool_call\n" +
                "{\"name\": \"write\", \"arguments\": \"{\\\"path\\\": \\\"/tmp/f.txt\\\"}\"}\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val fn = results[0]["function"] as Map<*, *>
        assertEquals("{\"path\": \"/tmp/f.txt\"}", fn["arguments"])
    }

    @Test
    fun `parse fenced tool_call with parameters instead of arguments key still works`() {
        val content =
            "```tool_call\n" +
                "{\"name\": \"write\", \"parameters\": {\"path\": \"/tmp/g.txt\"}}\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals("/tmp/g.txt", args["path"])
    }

    @Test
    fun `parse fenced tool_call with no name is silently skipped`() {
        val content =
            "```tool_call\n" +
                "{\"arguments\": {\"path\": \"/tmp/h.txt\"}}\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse fenced tool_call with invalid JSON is silently skipped`() {
        val content =
            "```tool_call\n" +
                "{ this is not valid JSON }\n" +
                "```"
        val results = RepoFileTextToolCallParser.parse(content)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse extracts a tool_call tag block with object arguments`() {
        val content = "<tool_call>{\"name\": \"write\", \"arguments\": {\"path\": \"/tmp/i.txt\"}}</tool_call>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val fn = results[0]["function"] as Map<*, *>
        assertEquals("write", fn["name"])
        val args = jacksonRead(fn["arguments"] as String)
        assertEquals("/tmp/i.txt", args["path"])
    }

    @Test
    fun `parse extracts a tool_call tag block with string arguments`() {
        val content = "<tool_call>{\"name\": \"write\", \"arguments\": \"{\\\"path\\\": \\\"/tmp/j.txt\\\"}\"}</tool_call>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val fn = results[0]["function"] as Map<*, *>
        assertEquals("{\"path\": \"/tmp/j.txt\"}", fn["arguments"])
    }

    @Test
    fun `parse tool_call tag with parameters instead of arguments still works`() {
        val content = "<tool_call>{\"name\": \"read\", \"parameters\": {\"path\": \"/tmp/k.txt\"}}</tool_call>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(1, results.size)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals("/tmp/k.txt", args["path"])
    }

    @Test
    fun `parse tool_call tag with no name is silently skipped`() {
        val content = "<tool_call>{\"arguments\": {\"path\": \"/tmp/l.txt\"}}</tool_call>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse tool_call tag with invalid JSON is silently skipped`() {
        val content = "<tool_call>not valid json</tool_call>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse supports all three formats in the same content`() {
        val content =
            "<function=write><parameter=path>/tmp/m.txt</parameter></function>\n" +
                "```tool_call\n" +
                "{\"name\": \"read\", \"arguments\": {\"path\": \"/tmp/n.txt\"}}\n" +
                "```\n" +
                "<tool_call>{\"name\": \"delete\", \"arguments\": {\"path\": \"/tmp/o.txt\"}}</tool_call>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(3, results.size)
        assertEquals("write", (results[0]["function"] as Map<*, *>)["name"])
        assertEquals("read", (results[1]["function"] as Map<*, *>)["name"])
        assertEquals("delete", (results[2]["function"] as Map<*, *>)["name"])
    }

    @Test
    fun `parse assigns sequential call_text ids starting at zero`() {
        val content =
            "<function=a><parameter=k>v</parameter></function>" +
                "<function=b><parameter=k>v</parameter></function>" +
                "<function=c><parameter=k>v</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertEquals(3, results.size)
        assertEquals("call_text_0", results[0]["id"])
        assertEquals("call_text_1", results[1]["id"])
        assertEquals("call_text_2", results[2]["id"])
    }

    @Test
    fun `parse coerces recursive=true string to a real boolean true`() {
        val content = "<function=ls><parameter=recursive>true</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals(true, args["recursive"])
    }

    @Test
    fun `parse coerces recursive=TRUE (uppercase) to a real boolean true`() {
        val content = "<function=ls><parameter=recursive>TRUE</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals(true, args["recursive"])
    }

    @Test
    fun `parse coerces recursive=false string to a real boolean false`() {
        val content = "<function=ls><parameter=recursive>false</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals(false, args["recursive"])
    }

    @Test
    fun `parse keeps recursive=other as a string`() {
        val content = "<function=ls><parameter=recursive>maybe</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals("maybe", args["recursive"])
    }

    @Test
    fun `parse keeps non-recursive string values as plain strings`() {
        val content = "<function=write><parameter=content>line1\nline2</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        val args = jacksonRead((results[0]["function"] as Map<*, *>)["arguments"] as String)
        assertEquals("line1\nline2", args["content"])
    }

    @Test
    fun `parse the result is never null`() {
        val content = "<function=write><parameter=path>/tmp/p.txt</parameter></function>"
        val results = RepoFileTextToolCallParser.parse(content)
        assertNotNull(results)
        assertEquals(1, results.size)
    }

    @Suppress("UNCHECKED_CAST")
    private fun jacksonRead(json: String): Map<String, Any?> =
        com.fasterxml.jackson.databind
            .ObjectMapper()
            .readValue(json, Map::class.java) as Map<String, Any?>
}
