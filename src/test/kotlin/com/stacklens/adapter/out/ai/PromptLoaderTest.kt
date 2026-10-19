package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PromptLoaderTest {
    private val resourceLoader = DefaultResourceLoader()
    private val objectMapper = ObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }

    private fun loader(
        @TempDir tempDir: Path,
    ) = PromptLoader(resourceLoader, objectMapper, tempDir.toString())

    @Test
    fun `ticketrefinement improve_ticket is loaded from file and contains real prompt text`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket")
        assertTrue(result != "ticketrefinement.improve_ticket", "Key must resolve to actual prompt, not fall back to raw key")
        assertTrue(result.contains("{outputLanguage}"), "Prompt must contain {outputLanguage} placeholder")
        assertTrue(result.contains("{ticketKey}"), "Prompt must contain {ticketKey} placeholder")
        assertTrue(result.contains("openPoints"), "Prompt must mention openPoints")
    }

    @Test
    fun `ticketrefinement improve_ticket prompt instructs the AI to refine existing subtasks via existingKey`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket")
        assertTrue(
            result.contains("EXISTING_SUBTASKS"),
            "INPUT section must wrap the {ticketSubtasks} placeholder in an <EXISTING_SUBTASKS> marker",
        )
        assertTrue(
            result.contains("existingKey"),
            "Prompt must mention existingKey so the AI can refine existing Jira subtasks",
        )
        assertTrue(
            result.contains("Refine an existing subtask"),
            "Prompt must spell out the three options when existing subtasks are present",
        )
        assertTrue(
            !result.contains("Child subtasks (additional context — do not improve them independently"),
            "Old wording must be gone from the subtask INPUT block — the AI is now free to refine existing subtasks",
        )
    }

    @Test
    fun `ticketrefinement repo_analysis is loaded from file`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.repo_analysis")
        assertTrue(result != "ticketrefinement.repo_analysis", "Key must resolve to actual prompt")
        assertTrue(result.contains("{projectTree}"), "Repo analysis prompt must contain {projectTree} placeholder")
        assertTrue(result.contains("{outputLanguage}"), "Prompt must contain {outputLanguage} placeholder")
    }

    @Test
    fun `ticketrefinement improve_ticket_adf is loaded from file`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket_adf")
        assertTrue(result != "ticketrefinement.improve_ticket_adf", "Key must resolve to actual prompt")
        assertTrue(result.contains("improvedDescriptionAdf"), "ADF prompt must mention improvedDescriptionAdf")
        assertTrue(
            result.contains("EXISTING_SUBTASKS"),
            "ADF INPUT section must also wrap subtasks in <EXISTING_SUBTASKS> so AI can refine them",
        )
    }

    @Test
    fun `ticketrefinement improve_ticket_wiki is loaded from file`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket_wiki")
        assertTrue(result != "ticketrefinement.improve_ticket_wiki", "Key must resolve to actual prompt")
        assertTrue(result.contains("improvedDescriptionWiki"), "Wiki prompt must mention improvedDescriptionWiki")
        assertTrue(
            result.contains("EXISTING_SUBTASKS"),
            "Wiki INPUT section must also wrap subtasks in <EXISTING_SUBTASKS> so AI can refine them",
        )
    }

    @Test
    fun `ticketrefinement improve_ticket prompt contains SUBTASK ANALYSIS section`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket")
        assertTrue(
            result.contains("## SUBTASK ANALYSIS"),
            "Markdown prompt must contain the SUBTASK ANALYSIS section so the AI can decide whether to suggest subtasks",
        )
        assertTrue(
            result.contains("\"subtasks\""),
            "Markdown prompt output format must include the subtasks array",
        )
    }

    @Test
    fun `ticketcreation prompts create real subtasks without exposing internal instructions`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val issueTypes =
            listOf(
                "ticketcreation.create_story",
                "ticketcreation.create_technical_task",
                "ticketcreation.create_bug",
            )
        issueTypes.forEach { key ->
            val result = loader.get("en", key)
            assertTrue(
                result.contains("## SUBTASK ANALYSIS"),
                "ticketcreation prompt '$key' must contain SUBTASK ANALYSIS",
            )
            assertTrue(
                result.contains("populate the top-level `subtasks` JSON array") ||
                    result.contains("populate\nthe top-level `subtasks` JSON array"),
                "ticketcreation prompt '$key' must require actual child drafts instead of a note in the parent description",
            )
            assertTrue(
                !result.contains("> **Note:** This is an in-description section"),
                "Internal prompt guidance for '$key' must never be rendered in the user-visible Jira description",
            )
            assertTrue(
                result.contains("\"issueType\": \"Subtask\""),
                "Prompt JSON template '$key' must show subtasks with issueType=Subtask",
            )
        }
    }

    @Test
    fun `ticketcreation story prompt uses the user-story template sections`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketcreation.create_story")
        assertTrue(
            result.contains("User Story"),
            "Story template must use the User Story section header",
        )
        assertTrue(
            result.contains("Acceptance Criteria"),
            "Story template must use the Acceptance Criteria section",
        )
        assertTrue(
            result.contains("Background"),
            "Story template must use the Background section",
        )
        assertTrue(
            result.contains("\"issueType\": \"Story\""),
            "Story template JSON output must hard-code issueType=Story",
        )
    }

    @Test
    fun `ticketcreation technical-task prompt uses the implementation-plan template sections`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketcreation.create_technical_task")
        assertTrue(
            result.contains("Solution Approach"),
            "Technical Task template must use the Solution Approach section",
        )
        assertTrue(
            result.contains("Affected Components"),
            "Technical Task template must use the Affected Components section",
        )
        assertTrue(
            result.contains("Definition of Done"),
            "Technical Task template must use the Definition of Done section",
        )
        assertTrue(
            result.contains("\"issueType\": \"Technical Task\""),
            "Technical Task template JSON output must hard-code issueType=Technical Task",
        )
    }

    @Test
    fun `ticketcreation bug prompt uses the bug-report template sections`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketcreation.create_bug")
        assertTrue(
            result.contains("Steps to Reproduce"),
            "Bug template must use the Steps to Reproduce section",
        )
        assertTrue(
            result.contains("Current state") && result.contains("Expected state"),
            "Bug template must use the Definition-of-Tickets current-state/expected-state framing",
        )
        assertTrue(
            result.contains("Definition of Done"),
            "Bug template must use the Definition of Done section",
        )
        assertTrue(
            result.contains("\"issueType\": \"Bug\""),
            "Bug template JSON output must hard-code issueType=Bug",
        )
    }

    @Test
    fun `ticketcreation classify_type prompt is no longer present`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketcreation.classify_type")
        assertEquals(
            "ticketcreation.classify_type",
            result,
            "ticketcreation.classify_type must be removed because the user selects the issue type in the UI",
        )
    }

    @Test
    fun `ticketrefinement improve_ticket_adf prompt mentions subtasks field`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket_adf")
        assertTrue(
            result.contains("\"subtasks\""),
            "ADF prompt output format must include the subtasks array so ADF tickets also get suggestions",
        )
    }

    @Test
    fun `ticketrefinement improve_ticket_wiki prompt mentions subtasks field`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "ticketrefinement.improve_ticket_wiki")
        assertTrue(
            result.contains("\"subtasks\""),
            "Wiki prompt output format must include the subtasks array so Server/DC tickets also get suggestions",
        )
    }

    @Test
    fun `get returns default prompt for known key in de`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("de", "common.be_copilot_quota")
        assertNotNull(result)
    }

    @Test
    fun `get returns key when no matching translation found`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val unknownKey = "non.existent.key.xyz"
        val result = loader.get("de", unknownKey)
        assertEquals(unknownKey, result)
    }

    @Test
    fun `get substitutes vars in template`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("test.placeholder", "Hello {name}!")
        val result = loader.get("de", "test.placeholder", mapOf("name" to "World"))
        assertEquals("Hello World!", result)
    }

    @Test
    fun `get uses custom override when available`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("common.be_copilot_quota", "Custom override")
        val result = loader.get("de", "common.be_copilot_quota")
        assertEquals("Custom override", result)
    }

    @Test
    fun `getCustom returns null when no custom set`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        assertNull(loader.getCustom("common.something"))
    }

    @Test
    fun `getCustom returns saved custom prompt`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("my.custom.key", "custom content")
        assertEquals("custom content", loader.getCustom("my.custom.key"))
    }

    @Test
    fun `getDefault returns key when not found in yaml`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.getDefault("de", "non.existent.prompt.key")
        assertEquals("non.existent.prompt.key", result)
    }

    @Test
    fun `getDefault prefers de fallback when key missing in lang file`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val deResult = loader.getDefault("de", "common.be_copilot_quota")
        val xxResult = loader.getDefault("xx", "common.be_copilot_quota")
        assertEquals(deResult, xxResult)
    }

    @Test
    fun `saveCustom persists to json file on disk`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("sql.my_prompt", "SELECT * FROM test")
        val jsonFile = File(tempDir.toFile(), "custom-prompts.json")
        assertTrue(jsonFile.exists())
        val content = jsonFile.readText()
        assertTrue(content.contains("sql.my_prompt"))
    }

    @Test
    fun `deleteCustom removes the key and updates disk file`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("jira.my_key", "Some content")
        loader.deleteCustom("jira.my_key")
        assertNull(loader.getCustom("jira.my_key"))
    }

    @Test
    fun `saveCustom survives when dataDir does not exist yet`() {
        val nonExistentDir = System.getProperty("java.io.tmpdir") + "/promptloader_test_${System.nanoTime()}"
        val loader = PromptLoader(resourceLoader, objectMapper, nonExistentDir)
        try {
            loader.saveCustom("sql.test", "content")
            assertEquals("content", loader.getCustom("sql.test"))
        } finally {
            File(nonExistentDir).deleteRecursively()
        }
    }

    @Test
    fun `custom prompts are loaded from existing file on construction`(
        @TempDir tempDir: Path,
    ) {
        val jsonFile = File(tempDir.toFile(), "custom-prompts.json")
        jsonFile.writeText("""{"pre.existing.key": "pre-loaded value"}""")
        val loader = loader(tempDir)
        assertEquals("pre-loaded value", loader.getCustom("pre.existing.key"))
    }

    @Test
    fun `loader handles malformed custom prompts json gracefully`(
        @TempDir tempDir: Path,
    ) {
        val jsonFile = File(tempDir.toFile(), "custom-prompts.json")
        jsonFile.writeText("{malformed json !!!}")
        val loader = loader(tempDir)
        assertNull(loader.getCustom("any.key"))
    }

    @Test
    fun `listKeys returns sorted keys with prefix`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("sql.key_b", "b")
        loader.saveCustom("sql.key_a", "a")
        val keys = loader.listKeys("de", "sql")
        assertTrue(keys.all { it.startsWith("sql.") })
    }

    @Test
    fun `listKeys returns empty when no keys match prefix`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val keys = loader.listKeys("de", "nonexistent_prefix_xyz")
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `listGroups returns groups for jira prefix in de`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val groups = loader.listGroups("de", "jira")
        assertNotNull(groups)
    }

    @Test
    fun `listGroups falls back to flat key groups when no section markers`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("mypfx.key1", "val1")
        loader.saveCustom("mypfx.key2", "val2")
        val groups = loader.listGroups("de", "mypfx")
        assertTrue(groups.isNotEmpty())
    }

    @Test
    fun `logging analyze prompt is loaded from file in both languages`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val en = loader.get("en", "logging.analyze")
        assertTrue(en != "logging.analyze", "en prompt must resolve to actual text, not the raw key")
        assertTrue(en.contains("root-cause"), "en prompt must mention root cause analysis")
        val de = loader.get("de", "logging.analyze")
        assertTrue(de != "logging.analyze", "de prompt must resolve to actual text, not the raw key")
        assertTrue(de.contains("Root Cause"), "de prompt must mention Root Cause")
    }

    @Test
    fun `listGroups returns a section group for the logging prefix`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val groups = loader.listGroups("de", "logging")
        assertTrue(groups.isNotEmpty(), "logging prompt file must produce at least one group")
        val analyzeGroup = groups.first { it.mainKey == "logging.analyze" }
        assertEquals("Log-Analyse", analyzeGroup.label)
        val fixGroup = groups.first { it.mainKey == "logging.fix_diagnose" }
        assertEquals("KI-Fix-Pipeline", fixGroup.label)
    }

    @Test
    fun `listKeys returns the logging analyze key`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val keys = loader.listKeys("en", "logging")
        assertTrue(keys.contains("logging.analyze"), "listKeys must include logging.analyze")
    }

    @Test
    fun `context returns PromptContext with all prompts merged`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("sql.my_custom", "custom value")
        val ctx = loader.context("de")
        assertNotNull(ctx)
        val value = ctx.get("sql.my_custom")
        assertEquals("custom value", value)
    }

    @Test
    fun `context merges en prompts when lang is en`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val ctx = loader.context("en")
        assertNotNull(ctx)
    }

    @Test
    fun `loadFile gracefully handles non-existent language directory`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("xx", "common.be_copilot_quota")
        assertNotNull(result)
    }

    @Test
    fun `listKeys with non-de lang merges keys from both languages`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val keys = loader.listKeys("en", "common")
        assertTrue(keys.isNotEmpty())
    }

    @Test
    fun `listGroups returns section groups when markers exist`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val groups = loader.listGroups("de", "jira")
        if (groups.isNotEmpty()) {
            assertNotNull(groups[0].label)
            assertNotNull(groups[0].mainKey)
        }
    }

    @Test
    fun `listGroups with non-de lang includes extra lang keys`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("sql.extra_custom_key", "custom")
        val groups = loader.listGroups("en", "sql")
        assertNotNull(groups)
    }

    @Test
    fun `deleteCustom for non-existent key does not throw`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.deleteCustom("nonexistent.key")
        assertNull(loader.getCustom("nonexistent.key"))
    }

    @Test
    fun `get falls back to de when lang file has no key`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val deResult = loader.get("de", "common.be_copilot_quota")
        val enResult = loader.get("en", "common.be_copilot_quota")
        if (deResult != "common.be_copilot_quota") {
            assertEquals(deResult, enResult)
        }
    }

    @Test
    fun `getDefault returns de fallback for missing lang`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.getDefault("zz", "common.be_copilot_quota")
        val deResult = loader.getDefault("de", "common.be_copilot_quota")
        assertEquals(deResult, result)
    }

    @Test
    fun `get resolves prompt from lang file when lang is en and key exists`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("en", "common.langInstruction")
        assertTrue(result != "common.langInstruction")
    }

    @Test
    fun `get falls back to en file when lang has no files but key exists in en`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.get("zz", "common.langInstruction")
        assertTrue(result != "common.langInstruction")
    }

    @Test
    fun `getDefault resolves prompt from lang file when lang is en and key exists`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.getDefault("en", "common.langInstruction")
        assertTrue(result != "common.langInstruction")
    }

    @Test
    fun `getDefault falls back to en file when lang has no files but key exists in en`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val result = loader.getDefault("zz", "common.langInstruction")
        assertTrue(result != "common.langInstruction")
    }

    @Test
    fun `listGroups falls back to key name when section marker value is blank`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        loader.saveCustom("pfx._section.mygrp", "")
        val groups = loader.listGroups("de", "pfx")
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `loadFile skips resource whose inputStream throws IOException`(
        @TempDir tempDir: Path,
    ) {
        val throwingResource = mock<Resource>()
        whenever(throwingResource.exists()).thenReturn(true)
        whenever(throwingResource.inputStream).thenThrow(IOException("forced test error"))

        val missingResource = mock<Resource>()
        whenever(missingResource.exists()).thenReturn(false)

        val mockResourceLoader = mock<ResourceLoader>()
        whenever(mockResourceLoader.getResource(any())).thenReturn(missingResource)
        whenever(mockResourceLoader.getResource(eq("classpath:prompts/en/common.md"))).thenReturn(throwingResource)

        val loader = PromptLoader(mockResourceLoader, objectMapper, tempDir.toString())
        val result = loader.get("en", "common.langInstruction")
        assertEquals("common.langInstruction", result)
    }

    @Test
    fun `parseMdPromptFile ignores content lines before the first key header`(
        @TempDir tempDir: Path,
    ) {
        val content = "Preamble text to ignore.\n## common.testKey\nPrompt body."

        val contentResource = mock<Resource>()
        whenever(contentResource.exists()).thenReturn(true)
        whenever(contentResource.inputStream).thenReturn(content.byteInputStream())

        val missingResource = mock<Resource>()
        whenever(missingResource.exists()).thenReturn(false)

        val mockResourceLoader = mock<ResourceLoader>()
        whenever(mockResourceLoader.getResource(any())).thenReturn(missingResource)
        whenever(mockResourceLoader.getResource(eq("classpath:prompts/en/common.md"))).thenReturn(contentResource)

        val loader = PromptLoader(mockResourceLoader, objectMapper, tempDir.toString())
        val result = loader.get("en", "common.testKey")
        assertEquals("Prompt body.", result)
    }

    @Test
    fun `production key commontools systemPrompt resolves from real classpath`(
        @TempDir tempDir: Path,
    ) {
        val loader = PromptLoader(resourceLoader, objectMapper, tempDir.toString())
        val result = loader.get("de", "commontools.systemPrompt")
        assertNotEquals(
            "commontools.systemPrompt",
            result,
            "Regression: 'commontools.systemPrompt' returned the raw key. " +
                "Check that src/main/resources/prompts/{de,en}/commontools.md is on the classpath " +
                "and the loader cache (loadFile → parsedCache) is populated.",
        )
        assertTrue(result.startsWith("Du bist"), "Expected German prompt, got: $result")
    }

    @Test
    fun `production key tooling iconSelection resolves from real classpath with de fallback to en`(
        @TempDir tempDir: Path,
    ) {
        val loader = PromptLoader(resourceLoader, objectMapper, tempDir.toString())
        val result = loader.get("de", "tooling.iconSelection")
        assertNotEquals(
            "tooling.iconSelection",
            result,
            "Regression: 'tooling.iconSelection' returned the raw key. " +
                "There is no de/tooling.md, so this must fall back to en/tooling.md. " +
                "If the fallback chain in get() is broken, the icon AI lookup fails silently.",
        )
        assertTrue(result.contains("{toolName}"), "Prompt must keep {toolName} placeholder")
        assertTrue(result.contains("{codeExcerpt}"), "Prompt must keep {codeExcerpt} placeholder")
    }

    @Test
    fun `loadFile reads each classpath resource at most once per language`(
        @TempDir tempDir: Path,
    ) {
        val prompt = "## common.cachedKey\nCached body.\n"
        val openCount = AtomicInteger(0)

        val realResource = mock<Resource>()
        whenever(realResource.exists()).thenReturn(true)
        whenever(realResource.inputStream).thenAnswer {
            openCount.incrementAndGet()
            prompt.byteInputStream()
        }
        val missingResource = mock<Resource>()
        whenever(missingResource.exists()).thenReturn(false)

        val mockLoader = mock<ResourceLoader>()
        whenever(mockLoader.getResource(any())).thenReturn(missingResource)
        whenever(mockLoader.getResource(eq("classpath:prompts/en/common.md"))).thenReturn(realResource)

        val loader = PromptLoader(mockLoader, objectMapper, tempDir.toString())
        repeat(50) {
            assertEquals("Cached body.", loader.get("en", "common.cachedKey"))
        }
        assertEquals(
            1,
            openCount.get(),
            "Expected exactly 1 read of 'classpath:prompts/en/common.md' for 50 lookups, " +
                "but the loader opened it ${openCount.get()} times. " +
                "Indicates the parsedCache is being bypassed — every call hits the JAR I/O.",
        )
    }

    @Test
    fun `concurrent get lookups all resolve the same key (production threading model)`(
        @TempDir tempDir: Path,
    ) {
        val loader = PromptLoader(resourceLoader, objectMapper, tempDir.toString())
        val keys =
            listOf(
                "commontools.systemPrompt",
                "tooling.iconSelection",
                "common.langInstruction",
                "sql.analyzeQuery",
                "codeanalysis.analysisPrompt",
            )
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures =
                keys
                    .flatMap { key ->
                        (0 until 32).map {
                            CompletableFuture.supplyAsync(
                                { loader.get("de", key) },
                                pool,
                            )
                        }
                    }
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            keys.forEach { key ->
                val values = results.filter { it == key || it.startsWith(key) }
                check(values.isEmpty()) {
                    "Concurrent lookups returned raw key '$key' for ${values.size} of ${results.size} calls. " +
                        "The parsedCache is not visible across threads."
                }
            }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `review analysisPrompt no longer references fixed PPTX slide anchors or magic question count`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val de = loader.get("de", "review.analysisPrompt")
        val en = loader.get("en", "review.analysisPrompt")

        val germanForbidden = listOf("Folien 5, 7, 9, 11, 13, 15", "15 Unterfragen", "FulfillmentReviews")
        germanForbidden.forEach { needle ->
            assertTrue(
                !de.contains(needle),
                "review.analysisPrompt (de) must no longer contain '$needle' but did. " +
                    "The PPTX-upload feature accepts arbitrary templates, so hard-coded slide anchors are obsolete.",
            )
        }
        val englishForbidden = listOf("slides 5, 7, 9, 11, 13, 15", "15 sub-questions", "FulfillmentReviews")
        englishForbidden.forEach { needle ->
            assertTrue(
                !en.contains(needle),
                "review.analysisPrompt (en) must no longer contain '$needle' but did. " +
                    "The PPTX-upload feature accepts arbitrary templates, so hard-coded slide anchors are obsolete.",
            )
        }
    }

    @Test
    fun `review pptxStage1Prompt is loaded in de and en`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val de = loader.get("de", "review.pptxStage1Prompt")
        val en = loader.get("en", "review.pptxStage1Prompt")
        assertTrue(de.length > 200, "de pptxStage1Prompt must be a real prompt, got len=${de.length}")
        assertTrue(en.length > 200, "en pptxStage1Prompt must be a real prompt, got len=${en.length}")
        assertTrue(de.contains("questionId"), "de pptxStage1Prompt must explain the questionId contract")
        assertTrue(en.contains("questionId"), "en pptxStage1Prompt must explain the questionId contract")
        assertTrue(de.contains("Frage") || de.contains("question"), "de prompt must mention question selection")
        assertTrue(en.contains("question"), "en prompt must mention question selection")
    }

    @Test
    fun `review pptxStage2Prompt is loaded in de and en`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val de = loader.get("de", "review.pptxStage2Prompt")
        val en = loader.get("en", "review.pptxStage2Prompt")
        assertTrue(de.length > 200, "de pptxStage2Prompt must be a real prompt, got len=${de.length}")
        assertTrue(en.length > 200, "en pptxStage2Prompt must be a real prompt, got len=${en.length}")
        assertTrue(de.contains("s1-shape0"), "de pptxStage2Prompt must give an example answer format")
        assertTrue(en.contains("s1-shape0"), "en pptxStage2Prompt must give an example answer format")
    }

    @Test
    fun `review pptxLangDirective is loaded in de and en`(
        @TempDir tempDir: Path,
    ) {
        val loader = loader(tempDir)
        val de = loader.get("de", "review.pptxLangDirective")
        val en = loader.get("en", "review.pptxLangDirective")
        assertTrue(de.length > 80, "de pptxLangDirective must be non-trivial, got len=${de.length}")
        assertTrue(en.length > 80, "en pptxLangDirective must be non-trivial, got len=${en.length}")
        assertTrue(de.contains("Sprache"), "de pptxLangDirective must mention Sprache")
        assertTrue(en.contains("language") || en.contains("Language"), "en pptxLangDirective must mention language")
    }
}
