package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtaskBodyFormatterTest {
    @Test
    fun `drops the copied User Story heading but keeps the story text`() {
        val formatted =
            SubtaskBodyFormatter.format(
                """
                ## User Story

                Als Bakin-Team möchten wir die Auftragsbeschreibung erweitern.

                ## AC

                - XML enthält Tenant-ID
                """.trimIndent(),
            )

        assertFalse(formatted.contains("User Story"), "subtasks must not carry a User Story header")
        assertTrue(formatted.startsWith("Als Bakin-Team"), "story text stays as a plain paragraph")
        assertTrue(formatted.contains("✅ AK:"))
        assertTrue(formatted.contains("- XML enthält Tenant-ID"))
    }

    @Test
    fun `recognises every common story heading variant`() {
        listOf(
            "# User Story",
            "## User Story:",
            "**User Story**",
            "User Story",
            "🧑‍💻 User Story",
            "## 🧑‍💻 User Story",
            "## Anwendergeschichte",
            "### Story",
        ).forEach { heading ->
            val formatted = SubtaskBodyFormatter.format("$heading\n\nAls Team wollen wir X.")
            assertFalse(formatted.contains("User Story", ignoreCase = true), "'$heading' must be removed")
            assertTrue(formatted.startsWith("Als Team wollen wir X."), "'$heading' must not eat the body")
        }
    }

    @Test
    fun `rewrites acceptance criteria markers to the jira green check`() {
        listOf(
            "AC",
            "AC:",
            "## AC",
            "**AC**",
            "### Acceptance Criteria",
            "## Abnahmekriterien",
            "## Akzeptanzkriterien",
            "✅ AC",
            "## ✅ Acceptance Criteria",
        ).forEach { marker ->
            val formatted = SubtaskBodyFormatter.format("$marker\n\n- criterion")
            assertTrue(
                formatted.startsWith("✅ AK:"),
                "'$marker' must become '✅ AK:', got: $formatted",
            )
        }
    }

    @Test
    fun `uses the AC marker for non-German tickets`() {
        val formatted =
            SubtaskBodyFormatter.format(
                "## User Story\n\nAs a team we want X.\n\n## AC\n\n- criterion",
                SubtaskBodyFormatter.ENGLISH_LABEL,
            )

        assertFalse(formatted.contains("User Story"))
        assertTrue(formatted.startsWith("As a team we want X."))
        assertTrue(formatted.contains("✅ AC:"))
    }

    @Test
    fun `labelForLanguage maps german to AK and english to AC`() {
        assertEquals("AK", SubtaskBodyFormatter.labelForLanguage("de"))
        assertEquals("AK", SubtaskBodyFormatter.labelForLanguage("DE"))
        assertEquals("AK", SubtaskBodyFormatter.labelForLanguage("de-DE"))
        assertEquals("AK", SubtaskBodyFormatter.labelForLanguage(""))
        assertEquals("AK", SubtaskBodyFormatter.labelForLanguage(null))
        assertEquals("AC", SubtaskBodyFormatter.labelForLanguage("en"))
        assertEquals("AC", SubtaskBodyFormatter.labelForLanguage("en-US"))
        assertEquals("AC", SubtaskBodyFormatter.labelForLanguage("fr"))
    }

    @Test
    fun `leaves list items prose and unrelated sections alone`() {
        val source =
            """
            ## 🔗 Parent Ticket
            BAKIN-38331

            ## 🔴 Hintergrundinformationen

            Die AC Liste ist bewusst kurz gehalten.

            - AC in a bullet stays a bullet
            - [ ] AC checkbox
            """.trimIndent()

        assertEquals(source, SubtaskBodyFormatter.format(source))
    }

    @Test
    fun `formatting is idempotent`() {
        val source = "## User Story\n\nAls Team wollen wir X.\n\n## AC\n\n- criterion\n\n---"
        val once = SubtaskBodyFormatter.format(source)
        assertEquals(once, SubtaskBodyFormatter.format(once))
        assertEquals("Als Team wollen wir X.\n\n✅ AK:\n\n- criterion\n\n---", once)
    }

    @Test
    fun `blank descriptions are returned untouched`() {
        assertEquals("", SubtaskBodyFormatter.format(""))
        assertEquals("   ", SubtaskBodyFormatter.format("   "))
    }

    @Test
    fun `a quoted heading line stays untouched as regular content`() {
        val source = "> ## User Story\n\nplain text"

        assertEquals(source, SubtaskBodyFormatter.format(source))
    }

    @Test
    fun `a bare acceptance label heading is rewritten without a phrase match`() {
        val formatted = SubtaskBodyFormatter.format("## AK\n\n- criterion", acceptanceLabel = "AC")

        assertEquals("\u2705 AC:\n\n- criterion", formatted)
    }

    @Test
    fun `an exact story heading is dropped without any prefix match`() {
        val formatted = SubtaskBodyFormatter.format("## Story\n\nAls Team wollen wir X.")

        assertEquals("Als Team wollen wir X.", formatted)
    }

    @Test
    fun `an acceptance phrase heading is rewritten through the phrase list`() {
        val formatted = SubtaskBodyFormatter.format("## Abnahme Kriterien\n\n- criterion")

        assertEquals("\u2705 AK:\n\n- criterion", formatted)
    }

    @Test
    fun `a story heading with a suffix is dropped through the prefix rule`() {
        val formatted = SubtaskBodyFormatter.format("## User Story (Backend)\n\nAls Team wollen wir X.")

        assertEquals("Als Team wollen wir X.", formatted)
    }
}
