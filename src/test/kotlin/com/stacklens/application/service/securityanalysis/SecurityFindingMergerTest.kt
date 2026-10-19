package com.stacklens.application.service.securityanalysis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.adapter.out.textfilter.DefaultAiJsonRepairer
import com.stacklens.adapter.out.textfilter.DefaultAiResponseJsonExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityFindingMergerTest {
    private lateinit var merger: SecurityFindingMerger
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val codec by lazy { JacksonJsonCodecAdapter(mapper) }

    @BeforeEach
    fun setUp() {
        merger = SecurityFindingMerger(codec, DefaultAiJsonRepairer(), DefaultAiResponseJsonExtractor())
    }

    @Test
    fun `mergeFindings adds new entries when no prior value exists`() {
        val existing = emptyMap<String, SecurityFinding>()
        val newFindings =
            listOf(
                SecurityFinding(id = "F-1", file = "a.kt", description = "d1"),
            )
        val result = merger.mergeFindings(existing, newFindings, iteration = 1)
        assertEquals(1, result.size)
        assertEquals("d1", result["F-1"]?.description)
    }

    @Test
    fun `mergeFindings enriches existing entry with new non-blank values`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", file = "a.kt", description = "old"))
        val newFindings =
            listOf(
                SecurityFinding(id = "F-1", description = "new", severity = "HIGH"),
            )
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        val merged = result["F-1"]!!
        assertEquals("new", merged.description)
        assertEquals("HIGH", merged.severity)
    }

    @Test
    fun `mergeFindings keeps prior values when new ones are blank`() {
        val existing =
            mapOf(
                "F-1" to
                    SecurityFinding(
                        id = "F-1",
                        description = "kept",
                        severity = "HIGH",
                        suggestion = "fix",
                        suggestedFix = "patch",
                    ),
            )
        val newFindings =
            listOf(
                SecurityFinding(id = "F-1", description = "", severity = "", suggestion = "", suggestedFix = null),
            )
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        val merged = result["F-1"]!!
        assertEquals("kept", merged.description)
        assertEquals("HIGH", merged.severity)
        assertEquals("fix", merged.suggestion)
        assertEquals("patch", merged.suggestedFix)
    }

    @Test
    fun `mergeFindings uses the new non-null suggestedFix when present`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", suggestedFix = "old"))
        val newFindings = listOf(SecurityFinding(id = "F-1", suggestedFix = "new"))
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        assertEquals("new", result["F-1"]?.suggestedFix)
    }

    @Test
    fun `mergeFindings keeps the prior suggestedFix when new one is null`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", suggestedFix = "old"))
        val newFindings = listOf(SecurityFinding(id = "F-1", suggestedFix = null))
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        assertEquals("old", result["F-1"]?.suggestedFix)
    }

    @Test
    fun `mergeFindings prefers new values for cwe, exploit, attack, cvss, exploitability, proofOfConcept`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", cwe = "CWE-1", attackVector = "old"))
        val newFindings =
            listOf(
                SecurityFinding(
                    id = "F-1",
                    cwe = "CWE-2",
                    exploitPayload = "p",
                    attackVector = "v",
                    attackSteps = listOf("s"),
                    proofOfConcept = "poc",
                    cvssScore = "9.0",
                    exploitability = "high",
                ),
            )
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        val merged = result["F-1"]!!
        assertEquals("CWE-2", merged.cwe)
        assertEquals("p", merged.exploitPayload)
        assertEquals("v", merged.attackVector)
        assertEquals(listOf("s"), merged.attackSteps)
        assertEquals("poc", merged.proofOfConcept)
        assertEquals("9.0", merged.cvssScore)
        assertEquals("high", merged.exploitability)
    }

    @Test
    fun `mergeFindings keeps prior values for the new-finding fields when the new finding sets them to null`() {
        val existing =
            mapOf(
                "F-1" to
                    SecurityFinding(
                        id = "F-1",
                        cwe = "CWE-1",
                        exploitPayload = "p",
                        attackVector = "v",
                        attackSteps = listOf("s"),
                        proofOfConcept = "poc",
                        cvssScore = "9.0",
                        exploitability = "high",
                    ),
            )
        val newFindings =
            listOf(
                SecurityFinding(
                    id = "F-1",
                    cwe = null,
                    exploitPayload = null,
                    attackVector = null,
                    attackSteps = null,
                    proofOfConcept = null,
                    cvssScore = null,
                    exploitability = null,
                ),
            )
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        val merged = result["F-1"]!!
        assertEquals("CWE-1", merged.cwe)
        assertEquals("p", merged.exploitPayload)
        assertEquals("v", merged.attackVector)
        assertEquals(listOf("s"), merged.attackSteps)
        assertEquals("poc", merged.proofOfConcept)
        assertEquals("9.0", merged.cvssScore)
        assertEquals("high", merged.exploitability)
    }

    @Test
    fun `mergeFindings OR-combines the confirmed flag across iterations`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", confirmed = false))
        val newFindings = listOf(SecurityFinding(id = "F-1", confirmed = true))
        val result = merger.mergeFindings(existing, newFindings, iteration = 2)
        assertTrue(result["F-1"]?.confirmed == true)
    }

    @Test
    fun `mergeFindings confirmed stays true when the new finding also has confirmed true`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", confirmed = true))
        val newFindings = listOf(SecurityFinding(id = "F-1", confirmed = true))
        val result = merger.mergeFindings(existing, newFindings, iteration = 3)
        assertTrue(result["F-1"]?.confirmed == true)
    }

    @Test
    fun `mergeFindings confirmed stays true when the new finding has confirmed false`() {
        val existing = mapOf("F-1" to SecurityFinding(id = "F-1", confirmed = true))
        val newFindings = listOf(SecurityFinding(id = "F-1", confirmed = false))
        val result = merger.mergeFindings(existing, newFindings, iteration = 3)
        assertTrue(result["F-1"]?.confirmed == true)
    }

    @Test
    fun `parseFindings returns empty when no FINDINGS block is present`() {
        assertTrue(merger.parseFindings("just some prose", iteration = 1).isEmpty())
    }

    @Test
    fun `parseFindings returns empty for a blank FINDINGS block`() {
        val raw = "FINDINGS:\n```json\n\n```"
        assertTrue(merger.parseFindings(raw, iteration = 1).isEmpty())
    }

    @Test
    fun `parseFindings returns empty when JSON block contains invalid JSON`() {
        val raw = "FINDINGS:\n```json\n{ not valid\n```"
        assertTrue(merger.parseFindings(raw, iteration = 1).isEmpty())
    }

    @Test
    fun `parseFindings salvages the valid entries when one array element is malformed`() {
        val raw =
            """
            FINDINGS:
            ```json
            [
              {"id":"F-1","file":"a.kt","description":"d"},
              {"id":"F-2","file":"b.kt","description":"d"},
              {"id":"F-3","file":"c.kt","description":"d"
              ["stray","array"]}
            ]
            ```
            """.trimIndent()

        val findings = merger.parseFindings(raw, iteration = 1)

        assertEquals(listOf("F-1", "F-2"), findings.map { it.id })
    }

    @Test
    fun `parseFindings salvages findings that carry escaped quotes and nested objects`() {
        val raw =
            """
            FINDINGS:
            ```json
            [
              {"id":"F-1","file":"a.kt","description":"d"},
              {"id":"F-2","description":"he said \"hi\"","details":{"inner":"x"}
              ["stray"]}
            ]
            ```
            """.trimIndent()

        val findings = merger.parseFindings(raw, iteration = 1)

        assertEquals(listOf("F-1"), findings.map { it.id })
    }

    @Test
    fun `parseFindings accepts a bare JSON array without a FINDINGS fence`() {
        val raw = "Here are the findings:\n[{\"id\":\"F-1\",\"file\":\"a.kt\",\"description\":\"d\"}]"

        val findings = merger.parseFindings(raw, iteration = 2)

        assertEquals(1, findings.size)
        assertEquals("F-1", findings[0].id)
        assertEquals(2, findings[0].iterationFound)
    }

    @Test
    fun `parseFindings decodes a valid JSON list and rewrites iterationFound to current iteration`() {
        val raw =
            """
            FINDINGS:
            ```json
            [
              {"id":"F-1","file":"a.kt","description":"d","iterationFound":1}
            ]
            ```
            """.trimIndent()
        val findings = merger.parseFindings(raw, iteration = 3)
        assertEquals(1, findings.size)
        assertEquals("F-1", findings[0].id)
        assertEquals(3, findings[0].iterationFound)
    }

    @Test
    fun `parseFindings keeps the value from the JSON when the finding is first seen in a later iteration`() {
        val raw =
            """
            FINDINGS:
            ```json
            [
              {"id":"F-1","file":"a.kt","description":"d","iterationFound":5}
            ]
            ```
            """.trimIndent()
        val findings = merger.parseFindings(raw, iteration = 1)
        assertEquals(5, findings[0].iterationFound)
    }

    @Test
    fun `parseFindings keeps a snippet that contains raw newlines from the model`() {
        val raw =
            """
            FINDINGS:
            ```json
            [
              {"id":"F-1","file":"a.kt","description":"d","snippet":"line one
            line two"}
            ]
            ```
            """.trimIndent()
        val findings = merger.parseFindings(raw, iteration = 1)
        assertEquals(1, findings.size)
        assertEquals("line one\nline two", findings[0].snippet)
    }

    @Test
    fun `parseFindings returns empty for an unterminated JSON block`() {
        val raw = "FINDINGS:\n```json\n[\"unterminated\n```"
        assertTrue(merger.parseFindings(raw, iteration = 1).isEmpty())
    }

    @Test
    fun `parseFindings without a language hint on the fence works as well`() {
        val raw =
            """
            FINDINGS:
            ```
            [{"id":"F-1","file":"a.kt","description":"d"}]
            ```
            """.trimIndent()
        val findings = merger.parseFindings(raw, iteration = 1)
        assertEquals(1, findings.size)
    }

    @Test
    fun `parseFindings keeps a finding whose payload carries an escape the model did not mean`() {
        val raw =
            """
            FINDINGS:
            ```json
            [
              {"id":"F-1","file":"a.kt","description":"d","exploitPayload":"curl 'http://h/api' \@host"}
            ]
            ```
            """.trimIndent()

        val findings = merger.parseFindings(raw, iteration = 3)

        assertEquals(1, findings.size)
        assertEquals("curl 'http://h/api' @host", findings[0].exploitPayload)
    }

    @Test
    fun `isAiSafetyRefusal returns true for known phrases`() {
        val phrases =
            listOf(
                "defensive auswertung",
                "Defensive Analysis",
                "exploit-payload",
                "Exploit Payload",
                "proof-of-concept",
                "Proof of Concept",
                "this is a poc",
                "ausnutzbar",
                "weaponizable",
                "schädlich",
                "harmful",
            )
        phrases.forEach { p ->
            assertTrue(merger.isAiSafetyRefusal(p), "Expected refusal for: $p")
        }
    }

    @Test
    fun `isAiSafetyRefusal is case-insensitive`() {
        assertTrue(merger.isAiSafetyRefusal("DEfEnSiVe AuSwErTuNg"))
    }

    @Test
    fun `isAiSafetyRefusal returns false for unrelated messages`() {
        assertFalse(merger.isAiSafetyRefusal("Hello world"))
    }

    @Test
    fun `isAiSafetyRefusal returns false for the empty string`() {
        assertFalse(merger.isAiSafetyRefusal(""))
    }
}
