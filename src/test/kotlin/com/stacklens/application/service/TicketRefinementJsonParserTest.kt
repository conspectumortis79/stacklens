package com.stacklens.application.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketRefinementJsonParserTest {
    private val codec = JacksonJsonCodecAdapter(jacksonObjectMapper())
    private val parser = TicketRefinementJsonParser(codec, DefaultAiJsonRepairer(), DefaultAiResponseJsonExtractor())

    @Test
    fun `ParsedReview applies correct defaults`() {
        val r = TicketRefinementJsonParser.ParsedReview("desc", listOf("a"))
        assertEquals("desc", r.improvedDescription)
        assertEquals(listOf("a"), r.openPoints)
        assertEquals("", r.improvedNative)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
        assertEquals("", r.improvedSummary)
    }

    @Test
    fun `ParsedReview stores all explicit values`() {
        val r = TicketRefinementJsonParser.ParsedReview("d", listOf("p"), "native", DescriptionFormat.ADF, "title")
        assertEquals("d", r.improvedDescription)
        assertEquals(listOf("p"), r.openPoints)
        assertEquals("native", r.improvedNative)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertEquals("title", r.improvedSummary)
    }

    @Test
    fun `flattenAdfPlain returns the node directly when given a String`() {
        assertEquals("hello", parser.flattenAdfPlain("hello"))
    }

    @Test
    fun `flattenAdfPlain returns empty string for null node`() {
        assertEquals("", parser.flattenAdfPlain(null))
    }

    @Test
    fun `flattenAdfPlain returns empty string for non-Map non-String node`() {
        assertEquals("", parser.flattenAdfPlain(42))
        assertEquals("", parser.flattenAdfPlain(listOf("a", "b")))
    }

    @Test
    fun `flattenAdfPlain ignores Map nodes whose type is not text and have no content`() {
        val node: Map<String, Any?> = mapOf("type" to "doc", "attrs" to mapOf("layout" to "wide"))
        assertEquals("", parser.flattenAdfPlain(node))
    }

    @Test
    fun `flattenAdfPlain flattens nested content from a non-text parent`() {
        val node: Map<String, Any?> =
            mapOf(
                "type" to "paragraph",
                "content" to listOf(mapOf("type" to "text", "text" to "inside")),
            )
        assertEquals("inside", parser.flattenAdfPlain(node))
    }

    @Test
    fun `flattenAdfPlain keeps text when node is a text node with non-string text`() {
        val node: Map<String, Any?> =
            mapOf(
                "type" to "text",
                "text" to 42,
            )
        assertEquals("", parser.flattenAdfPlain(node))
    }

    @Test
    fun `flattenAdfPlain keeps text when text field is null`() {
        val node: Map<String, Any?> =
            mapOf(
                "type" to "text",
            )
        assertEquals("", parser.flattenAdfPlain(node))
    }

    @Test
    fun `parseAiResponse default sourceFormat returns markdown via markdownFallback`() {
        val r = parser.parseAiResponse("""{"improvedDescription":"hello","openPoints":["x"]}""")
        assertEquals("hello", r.improvedDescription)
        assertEquals(listOf("x"), r.openPoints)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
        assertEquals("", r.improvedNative)
    }

    @Test
    fun `parseAiResponse explicit text sourceFormat is also markdown`() {
        val r = parser.parseAiResponse("""{"improvedDescription":"hi","openPoints":[]}""", DescriptionFormat.MARKDOWN)
        assertEquals("hi", r.improvedDescription)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse captures improvedSummary when present`() {
        val raw = """{"improvedDescription":"d","openPoints":[],"improvedSummary":"title"}"""
        val r = parser.parseAiResponse(raw)
        assertEquals("title", r.improvedSummary)
    }

    @Test
    fun `parseAiResponse drops null and whitespace-only openPoints`() {
        val raw = """{"improvedDescription":"x","openPoints":["ok",null,"","  "]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(listOf("ok"), r.openPoints)
    }

    @Test
    fun `parseAiResponse handles non-list openPoints`() {
        val raw = """{"improvedDescription":"x","openPoints":"not-a-list"}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(emptyList<String>(), r.openPoints)
    }

    @Test
    fun `parseAiResponse adf sourceFormat with valid doc node returns ADF`() {
        val adf = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hi"}]}]}"""
        val raw = """{"improvedDescriptionAdf":$adf,"openPoints":[]}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertTrue(r.improvedNative.contains("\"doc\""))
    }

    @Test
    fun `parseAiResponse adf sourceFormat with map node but type not doc falls back to markdown`() {
        val raw = """{"improvedDescriptionAdf":{"type":"paragraph"},"openPoints":[],"improvedDescription":"fb"}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
        assertEquals("fb", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse adf sourceFormat with non-map adf node falls back`() {
        val raw = """{"improvedDescriptionAdf":"string-not-map","openPoints":[],"improvedDescription":"fb"}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse wiki sourceFormat with non-blank wiki string`() {
        val raw = """{"improvedDescriptionWiki":"h1. Title","openPoints":[]}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.WIKI)
        assertEquals(DescriptionFormat.WIKI, r.improvedFormat)
        assertEquals("h1. Title", r.improvedDescription)
        assertEquals("h1. Title", r.improvedNative)
    }

    @Test
    fun `parseAiResponse wiki sourceFormat with blank wiki falls back to markdown`() {
        val raw = """{"improvedDescriptionWiki":"","openPoints":[],"improvedDescription":"fb"}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.WIKI)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
        assertEquals("fb", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse wiki sourceFormat with non-string wiki falls back`() {
        val raw = """{"improvedDescriptionWiki":42,"openPoints":[],"improvedDescription":"fb"}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.WIKI)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf sourceFormat falls back via extractAdfBlock when map is null`() {
        val adf = """{"type":"doc","content":[]}"""
        val raw = "GARBAGE {\"improvedDescriptionAdf\": $adf GARBAGE"
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertTrue(r.improvedNative.contains("\"doc\""))
    }

    @Test
    fun `parseAiResponse adf sourceFormat adfMap type not doc falls through to regex`() {
        val raw = "GARBAGE {\"improvedDescriptionAdf\": {\"type\":\"paragraph\"} GARBAGE"
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.MARKDOWN, r.improvedFormat)
    }

    @Test
    fun `parseAiResponse adf sourceFormat extractAdfBlock null for both attempts`() {
        val raw = "not parseable json {\"improvedDescription\":\"via-regex\"} extra"
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals("via-regex", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse falls back to raw text on completely malformed JSON`() {
        val r = parser.parseAiResponse("not json at all")
        assertEquals("not json at all", r.improvedDescription)
        assertEquals(emptyList<String>(), r.openPoints)
    }

    @Test
    fun `parseAiResponse descMatch regex extracts improvedDescription from garbled JSON`() {
        val raw = """some garbage {"improvedDescription":"found","extra":} more garbage"""
        val r = parser.parseAiResponse(raw)
        assertEquals("found", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse uses trimmed raw when no regex match found`() {
        val r = parser.parseAiResponse("  no matching key anywhere  ")
        assertEquals("no matching key anywhere", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse strips json fence before parsing`() {
        val raw = "```json\n{\"improvedDescription\":\"fenced\",\"openPoints\":[]}\n```"
        val r = parser.parseAiResponse(raw)
        assertEquals("fenced", r.improvedDescription)
    }

    @Test
    fun `sanitizeAiJson returns empty string for empty input`() {
        assertEquals("", parser.sanitizeAiJson(""))
    }

    @Test
    fun `sanitizeAiJson passes through plain ASCII unchanged`() {
        val json = """{"key":"value","n":1}"""
        assertEquals(json, parser.sanitizeAiJson(json))
    }

    @Test
    fun `sanitizeAiJson replaces fullwidth comma outside string`() {
        assertEquals(",", parser.sanitizeAiJson("\uFF0C"))
    }

    @Test
    fun `sanitizeAiJson replaces fullwidth colon outside string`() {
        assertEquals(":", parser.sanitizeAiJson("\uFF1A"))
    }

    @Test
    fun `sanitizeAiJson replaces fullwidth semicolon outside string`() {
        assertEquals(";", parser.sanitizeAiJson("\uFF1B"))
    }

    @Test
    fun `sanitizeAiJson replaces smart double quotes outside string`() {
        assertEquals("\"hello\"", parser.sanitizeAiJson("\u201Chello\u201D"))
        assertEquals("\"hi\"", parser.sanitizeAiJson("\u201Ehi\u201F"))
    }

    @Test
    fun `sanitizeAiJson replaces smart single quotes outside string`() {
        assertEquals("'hi'", parser.sanitizeAiJson("\u2018hi\u2019"))
        assertEquals("'lo'", parser.sanitizeAiJson("\u201Alo\u201B"))
    }

    @Test
    fun `sanitizeAiJson replaces ideographic space outside string`() {
        assertEquals(" ", parser.sanitizeAiJson("\u3000"))
    }

    @Test
    fun `sanitizeAiJson does not replace special chars inside a string literal`() {
        val input = "{\"key\":\"\uFF0C\"}"
        assertEquals("{\"key\":\"\uFF0C\"}", parser.sanitizeAiJson(input))
    }

    @Test
    fun `sanitizeAiJson handles backslash-quote escape inside string (escape path)`() {
        val input = "{\"key\":\"val\\\"inner\"}"
        assertEquals(input, parser.sanitizeAiJson(input))
    }

    @Test
    fun `sanitizeAiJson handles double backslash inside string`() {
        val input = "{\"key\":\"a\\\\b\"}"
        assertEquals(input, parser.sanitizeAiJson(input))
    }

    @Test
    fun `repairUnbalancedQuotes returns empty string for empty input`() {
        assertEquals("", parser.repairUnbalancedQuotes(""))
    }

    @Test
    fun `repairUnbalancedQuotes leaves well-formed JSON unchanged`() {
        val json = """{"key":"value","num":42}"""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `repairUnbalancedQuotes escapes stray quote before content character`() {
        val json = """{"key":"val"ue"}"""
        val result = parser.repairUnbalancedQuotes(json)
        assertTrue(result.contains("\\\""))
    }

    @Test
    fun `repairUnbalancedQuotes quote before comma is terminator`() {
        val json = """{"a":"x","b":"y"}"""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `repairUnbalancedQuotes quote before colon is terminator`() {
        val json = """{"key":"value"}"""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `repairUnbalancedQuotes quote before closing brace is terminator`() {
        val json = """{"k":"v"}"""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `repairUnbalancedQuotes quote before closing bracket is terminator`() {
        val json = """{"arr":["x","y"]}"""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `repairUnbalancedQuotes quote at end of string is terminator (null char)`() {
        val json = "{\"k\":\"v\""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `repairUnbalancedQuotes skips whitespace before checking terminator`() {
        val json = "{\"k\":\"v\" }"
        val result = parser.repairUnbalancedQuotes(json)
        assertNotNull(result)
        assertTrue(!result.contains("\\\""))
    }

    @Test
    fun `repairUnbalancedQuotes handles backslash-quote escape in string`() {
        val json = """{"key":"val\"ue"}"""
        val result = parser.repairUnbalancedQuotes(json)
        assertNotNull(result)
        assertTrue(result.contains("\\\""))
    }

    @Test
    fun `repairUnbalancedQuotes handles backslash not followed by quote`() {
        val json = """{"key":"line\nfeed"}"""
        assertEquals(json, parser.repairUnbalancedQuotes(json))
    }

    @Test
    fun `tryRepairTruncatedJson returns null when input is already balanced`() {
        val s = """{"key":"val"}"""
        assertNull(parser.tryRepairTruncatedJson(s))
    }

    @Test
    fun `tryRepairTruncatedJson returns null for empty string`() {
        assertNull(parser.tryRepairTruncatedJson(""))
    }

    @Test
    fun `tryRepairTruncatedJson returns null for nested-but-balanced object`() {
        val s = """{"a":{"b":[{"c":1}]}}"""
        assertNull(parser.tryRepairTruncatedJson(s))
    }

    @Test
    fun `tryRepairTruncatedJson closes open brace at end`() {
        val s = """{"key":"val"""
        val r = parser.tryRepairTruncatedJson(s)
        assertNotNull(r)
        val map = codec.decodeMap(r!!)
        assertEquals("val", map["key"])
    }

    @Test
    fun `tryRepairTruncatedJson closes nested unclosed braces`() {
        val s = """{"a":{"b":{"c":"v"""
        val r = parser.tryRepairTruncatedJson(s)
        assertNotNull(r)
        val map = codec.decodeMap(r!!)
        val a = map["a"] as Map<*, *>
        val b = a["b"] as Map<*, *>
        assertEquals("v", b["c"])
    }

    @Test
    fun `tryRepairTruncatedJson closes open bracket and brace in LIFO order`() {
        val s = """{"arr":[{"k":"v"""
        val r = parser.tryRepairTruncatedJson(s)
        assertNotNull(r)
        val map = codec.decodeMap(r!!)
        val arr = map["arr"] as List<*>
        assertEquals(1, arr.size)
        val item = arr[0] as Map<*, *>
        assertEquals("v", item["k"])
    }

    @Test
    fun `tryRepairTruncatedJson cuts off mid-string-literal and closes the value`() {
        val truncated =
            """{"improvedDescriptionAdf":{"type":"doc","content":[{"type":"text","text":""""
        val r = parser.tryRepairTruncatedJson(truncated)
        assertNotNull(r)
        val map = codec.decodeMap(r!!)
        val adf = map["improvedDescriptionAdf"] as Map<*, *>
        assertEquals("doc", adf["type"])
    }

    @Test
    fun `tryRepairTruncatedJson does not count braces inside string literals`() {
        val s = """{"text":"has { and } inside"""
        val r = parser.tryRepairTruncatedJson(s)
        assertNotNull(r)
        val map = codec.decodeMap(r!!)
        assertEquals("has { and } inside", map["text"])
    }

    @Test
    fun `tryRepairTruncatedJson handles escaped quote inside open string`() {
        val s = """{"text":"say \"hi"""
        val r = parser.tryRepairTruncatedJson(s)
        assertNotNull(r)
        val map = codec.decodeMap(r!!)
        assertEquals("""say "hi""", map["text"])
    }

    @Test
    fun `extractAdfBlock returns null when key is absent`() {
        assertNull(parser.extractAdfBlock("""{"other":"value"}"""))
    }

    @Test
    fun `extractAdfBlock returns null when no colon follows the key`() {
        assertNull(parser.extractAdfBlock("{\"improvedDescriptionAdf\"}"))
    }

    @Test
    fun `extractAdfBlock returns null when value is not an object`() {
        assertNull(parser.extractAdfBlock("""{"improvedDescriptionAdf": "string"}"""))
    }

    @Test
    fun `extractAdfBlock returns null when string ends in whitespace after colon`() {
        assertNull(parser.extractAdfBlock("{\"improvedDescriptionAdf\":   "))
    }

    @Test
    fun `extractAdfBlock returns null for unclosed object`() {
        assertNull(parser.extractAdfBlock("""{"improvedDescriptionAdf": {"type":"doc" """))
    }

    @Test
    fun `extractAdfBlock extracts a simple object`() {
        val json = """{"improvedDescriptionAdf": {"type": "doc"}}"""
        val result = parser.extractAdfBlock(json)
        assertEquals("""{"type": "doc"}""", result)
    }

    @Test
    fun `extractAdfBlock handles nested braces (depth tracking)`() {
        val json = """{"improvedDescriptionAdf": {"type":"doc","content":[{"type":"text"}]}}"""
        val result = parser.extractAdfBlock(json)
        assertEquals("""{"type":"doc","content":[{"type":"text"}]}""", result)
    }

    @Test
    fun `extractAdfBlock string containing braces does not confuse depth`() {
        val json = """{"improvedDescriptionAdf": {"type":"doc","text":"has {braces} inside"}}"""
        val result = parser.extractAdfBlock(json)
        assertNotNull(result)
        assertTrue(result!!.contains("has {braces} inside"))
    }

    @Test
    fun `extractAdfBlock handles escape sequence inside string value`() {
        val json = """{"improvedDescriptionAdf": {"type":"doc","text":"say \"hi\""}}"""
        val result = parser.extractAdfBlock(json)
        assertNotNull(result)
        assertTrue(result!!.contains("say"))
    }

    @Test
    fun `extractJsonBlock extracts from json code fence`() {
        val s = "```json\n{\"key\":\"val\"}\n```"
        assertEquals("""{"key":"val"}""", parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock extracts from JSON uppercase fence`() {
        val s = "```JSON\n{\"key\":\"val\"}\n```"
        assertEquals("""{"key":"val"}""", parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock extracts from plain code fence`() {
        val s = "```\n{\"key\":\"val\"}\n```"
        assertEquals("""{"key":"val"}""", parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock handles fence with no closing backticks`() {
        val s = "```json\n{\"key\":\"val\"}"
        assertTrue(parser.extractJsonBlock(s).contains("\"key\""))
    }

    @Test
    fun `extractJsonBlock fence content with no braces falls through to outer search`() {
        val s = "```\nno braces at all\n```"
        assertEquals(s, parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock no fence extracts brace substring`() {
        val s = "some text {\"key\":\"val\"} more text"
        assertEquals("""{"key":"val"}""", parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock no fence and no braces returns original`() {
        val s = "no json here"
        assertEquals(s, parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock only opening brace returns original`() {
        val s = "{ no closing brace"
        assertEquals(s, parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock fence content with opening brace but no closing brace returns fence-stripped block`() {
        val s = "```json\n{ unclosed\n```"
        assertEquals("{ unclosed", parser.extractJsonBlock(s))
    }

    @Test
    fun `extractJsonBlock unclosed opening fence without closing fence returns fence-stripped block`() {
        val s = "```json\n{\"improvedSummary\":\"s\""
        assertEquals("{\"improvedSummary\":\"s\"", parser.extractJsonBlock(s))
    }

    @Test
    fun `parseAiResponse markdown sourceFormat captures subtasks array`() {
        val raw =
            """{"improvedDescription":"x","openPoints":[],"subtasks":[{"summary":"Sub A","description":"Do X","priority":"Minor","labels":["backend"]}]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(1, r.suggestedSubtasks.size)
        val s = r.suggestedSubtasks[0]
        assertEquals("Sub A", s.summary)
        assertEquals("Do X", s.description)
        assertEquals("Subtask", s.issueType)
        assertEquals("Minor", s.priority)
        assertEquals(listOf("backend"), s.labels)
        assertEquals("", s.existingKey)
    }

    @Test
    fun `parseAiResponse adf sourceFormat captures subtasks with native format`() {
        val raw =
            """{"improvedDescriptionAdf":{"type":"doc"},"openPoints":[],"subtasks":[{"summary":"Sub ADF"}]}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertEquals(1, r.suggestedSubtasks.size)
        assertEquals("Sub ADF", r.suggestedSubtasks[0].summary)
    }

    @Test
    fun `parseAiResponse wiki sourceFormat captures subtasks`() {
        val raw =
            """{"improvedDescriptionWiki":"h1. Title","openPoints":[],"subtasks":[{"summary":"Sub Wiki"}]}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.WIKI)
        assertEquals(DescriptionFormat.WIKI, r.improvedFormat)
        assertEquals(1, r.suggestedSubtasks.size)
    }

    @Test
    fun `parseAiResponse returns empty subtasks when AI omitted the field`() {
        val raw = """{"improvedDescription":"x","openPoints":[]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(emptyList<Any>(), r.suggestedSubtasks)
    }

    @Test
    fun `parseAiResponse returns empty subtasks when AI returned empty array`() {
        val raw = """{"improvedDescription":"x","openPoints":[],"subtasks":[]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(emptyList<Any>(), r.suggestedSubtasks)
    }

    @Test
    fun `parseAiResponse skips subtask entries with blank summary`() {
        val raw =
            """{"improvedDescription":"x","openPoints":[],"subtasks":[{"summary":"  "},{"summary":"Keep me"}]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(1, r.suggestedSubtasks.size)
        assertEquals("Keep me", r.suggestedSubtasks[0].summary)
    }

    @Test
    fun `parseAiResponse captures existingKey for refinement suggestions`() {
        val raw =
            """{"improvedDescription":"x","openPoints":[],"subtasks":[{"summary":"Refined title","existingKey":"PROJ-99"}]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(1, r.suggestedSubtasks.size)
        assertEquals("PROJ-99", r.suggestedSubtasks[0].existingKey)
    }

    @Test
    fun `parseAiResponse normalises blank issueType and priority to defaults`() {
        val raw =
            """{"improvedDescription":"x","openPoints":[],"subtasks":[{"summary":"S","issueType":"","priority":"  "}]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals("Subtask", r.suggestedSubtasks[0].issueType)
        assertEquals("Minor", r.suggestedSubtasks[0].priority)
    }

    @Test
    fun `parseAiResponse filters blank labels but keeps valid ones`() {
        val raw =
            """{"improvedDescription":"x","openPoints":[],"subtasks":[{"summary":"S","labels":["ok","  ",""]}]}"""
        val r = parser.parseAiResponse(raw)
        assertEquals(listOf("ok"), r.suggestedSubtasks[0].labels)
    }

    @Test
    fun `parseSubtasks returns empty list for null input`() {
        assertEquals(emptyList<Any>(), parser.parseSubtasks(null))
    }

    @Test
    fun `parseSubtasks returns empty list for non-list input`() {
        assertEquals(emptyList<Any>(), parser.parseSubtasks(mapOf("summary" to "x")))
        assertEquals(emptyList<Any>(), parser.parseSubtasks("string"))
        assertEquals(emptyList<Any>(), parser.parseSubtasks(42))
    }

    @Test
    fun `parseSubtasks skips non-map entries and blank summaries`() {
        val list: List<Any?> =
            listOf(
                "not-a-map",
                mapOf("summary" to ""),
                mapOf("summary" to "   "),
                mapOf("summary" to "Valid"),
                42,
            )
        val out = parser.parseSubtasks(list)
        assertEquals(1, out.size)
        assertEquals("Valid", out[0].summary)
    }

    @Test
    fun `parseSubtasks normalises the story header and the AC marker`() {
        val out =
            parser.parseSubtasks(
                listOf(
                    mapOf(
                        "summary" to "Child",
                        "description" to "## User Story\n\nAls Team wollen wir X.\n\n## AC\n\n- criterion",
                    ),
                ),
            )
        assertEquals("Als Team wollen wir X.\n\n✅ AK:\n\n- criterion", out[0].description)
    }

    @Test
    fun `parseSubtasks honours the requested acceptance criteria label`() {
        val out =
            parser.parseSubtasks(
                listOf(mapOf("summary" to "Child", "description" to "AC\n\n- criterion")),
                SubtaskBodyFormatter.ENGLISH_LABEL,
            )
        assertEquals("✅ AC:\n\n- criterion", out[0].description)
    }

    @Test
    fun `parseSubtasks keeps an empty description empty`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "Child", "description" to "   ")))
        assertEquals("   ", out[0].description)
    }

    @Test
    fun `parseSubtasks handles non-string summary via as cast fallback`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to 42, "issueType" to "Subtask")))
        assertEquals(0, out.size)
    }

    @Test
    fun `parseSubtasks defaults issueType to Subtask when blank`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "x", "issueType" to "")))
        assertEquals("Subtask", out[0].issueType)
    }

    @Test
    fun `parseSubtasks defaults issueType to Subtask when null`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "x")))
        assertEquals("Subtask", out[0].issueType)
    }

    @Test
    fun `parseSubtasks defaults issueType to Subtask when non-string value`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "x", "issueType" to 42)))
        assertEquals("Subtask", out[0].issueType)
    }

    @Test
    fun `parseSubtasks strips blank labels and keeps non-string entries`() {
        val out =
            parser.parseSubtasks(
                listOf(
                    mapOf(
                        "summary" to "x",
                        "labels" to listOf("ok", "", "   ", 42, "another"),
                    ),
                ),
            )
        assertEquals(1, out.size)
        assertEquals(listOf("ok", "another"), out[0].labels)
    }

    @Test
    fun `parseSubtasks uses empty labels when labels is non-list`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "x", "labels" to "not-a-list")))
        assertEquals(emptyList<String>(), out[0].labels)
    }

    @Test
    fun `parseSubtasks propagates existingKey`() {
        val out =
            parser.parseSubtasks(
                listOf(mapOf("summary" to "x", "existingKey" to "PROJ-99")),
            )
        assertEquals("PROJ-99", out[0].existingKey)
    }

    @Test
    fun `parseSubtasks defaults existingKey to empty when non-string`() {
        val out =
            parser.parseSubtasks(
                listOf(mapOf("summary" to "x", "existingKey" to 42)),
            )
        assertEquals("", out[0].existingKey)
    }

    @Test
    fun `parseSubtasks defaults priority to Minor when null`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "x")))
        assertEquals("Minor", out[0].priority)
    }

    @Test
    fun `parseSubtasks keeps non-default issueType when provided`() {
        val out = parser.parseSubtasks(listOf(mapOf("summary" to "x", "issueType" to "Bug")))
        assertEquals("Bug", out[0].issueType)
    }

    @Test
    fun `parseAiResponse picks up improvedDescriptionAdf when sourceFormat is text`() {
        val adf = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hi"}]}]}"""
        val raw = """{"improvedSummary":"s","improvedDescriptionAdf":$adf,"openPoints":[]}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.MARKDOWN)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertEquals("hi", r.improvedDescription)
        assertTrue(r.improvedNative.contains("\"doc\""))
    }

    @Test
    fun `parseAiResponse picks up improvedDescriptionAdf when sourceFormat is wiki`() {
        val adf = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hi"}]}]}"""
        val raw = """{"improvedSummary":"s","improvedDescriptionAdf":$adf,"openPoints":[]}"""
        val r = parser.parseAiResponse(raw, DescriptionFormat.WIKI)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertEquals("hi", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse picks up improvedDescriptionAdf wrapped in a code fence`() {
        val adf = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hi"}]}]}"""
        val raw =
            "```json\n" +
                """{"improvedSummary":"s","improvedDescriptionAdf":$adf,"openPoints":[]}""" +
                "\n```"
        val r = parser.parseAiResponse(raw, DescriptionFormat.MARKDOWN)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertEquals("hi", r.improvedDescription)
    }

    @Test
    fun `parseAiResponse repairs AI output truncated mid-string-literal and picks up the ADF`() {
        val truncated =
            "```json\n" +
                "{\n" +
                "  \"improvedSummary\": \"Bewertung MySQL/MariaDB-Datenbanken für Cloud-Migration\",\n" +
                "  \"improvedDescriptionAdf\": {\n" +
                "    \"type\": \"doc\",\n" +
                "    \"version\": 1,\n" +
                "    \"content\": [\n" +
                "      {\n" +
                "        \"type\": \"text\",\n" +
                "        \"text\": \""
        val r = parser.parseAiResponse(truncated, DescriptionFormat.ADF)
        assertEquals(DescriptionFormat.ADF, r.improvedFormat)
        assertTrue(r.improvedNative.contains("\"doc\""))
        assertEquals(
            "Bewertung MySQL/MariaDB-Datenbanken für Cloud-Migration",
            r.improvedSummary,
        )
    }
}
