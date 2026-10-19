package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketCreationResultNormalizerTest {
    private val normalizer =
        TicketCreationResultNormalizer(
            sectionStrategies =
                listOf(
                    com.stacklens.application.service.ticketcreation
                        .TechnicalTaskSectionStrategy(),
                    com.stacklens.application.service.ticketcreation
                        .StoryBugSectionStrategy(),
                ),
        )

    @Test
    fun `normalize keeps explicit subtasks and removes internal prompt note from description`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Parent",
                    "description" to
                        """## 📋 Steps / Sub-tasks
> **Note:** This is an in-description section listing implementation steps.
> It is NOT the Jira V3 subtasks field.

- Implement API
- Add tests

---""",
                    "issueType" to "Technical Task",
                    "subtasks" to listOf(mapOf("summary" to "Explicit child")),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(1, subtasks.size)
        assertEquals("Explicit child", subtasks.single()["summary"])
        assertFalse((result["description"] as String).contains("This is an in-description section"))
        assertTrue((result["description"] as String).contains("- Implement API"))
    }

    @Test
    fun `normalize infers editable Jira subtasks from German implementation steps`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "JWT-Authentifizierung einführen",
                    "description" to
                        """## 🛠 Technisches Ziel
JWT-Authentifizierung bereitstellen.

---

## 📋 Schritte / Teilaufgaben
- JWT-Bibliothek integrieren
- Login-Endpunkt implementieren
- Integrations- und Unit-Tests ergänzen

---""",
                    "issueType" to "Technical Task",
                    "priority" to "Major",
                    "labels" to listOf("backend"),
                    "subtasks" to emptyList<Any>(),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(3, subtasks.size)
        assertEquals("JWT-Bibliothek integrieren", subtasks[0]["summary"])
        assertEquals("Subtask", subtasks[0]["issueType"])
        assertEquals("Major", subtasks[0]["priority"])
        assertEquals(listOf("backend"), subtasks[0]["labels"])
        assertTrue((subtasks[0]["description"] as String).contains("JWT-Authentifizierung bereitstellen."))
        assertTrue((subtasks[0]["description"] as String).trimEnd().endsWith("---"))
    }

    @Test
    fun `normalize infers Story subtasks from acceptance criteria when AI omitted them`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Self-service login",
                    "description" to
                        """## 🧑‍💻 User Story
As a customer I want to log in.

---

## ✅ Acceptance Criteria
- Login succeeds with valid credentials
- Invalid credentials show an error

---""",
                    "issueType" to "Story",
                    "priority" to "",
                    "subtasks" to emptyList<Any>(),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("Minor", subtasks[0]["priority"])
        assertEquals("Login succeeds with valid credentials", subtasks[0]["summary"])
        assertTrue((subtasks[1]["description"] as String).contains("✅ AK:"))
        assertFalse((subtasks[1]["description"] as String).contains("## ✅ Acceptance Criteria"))
    }

    @Test
    fun `companion object properties are reachable`() {
        val companionClass = Class.forName("com.stacklens.application.service.TicketCreationResultNormalizer\$Companion")
        val outerCls = TicketCreationResultNormalizer::class.java
        val companionField = outerCls.getDeclaredField("Companion")
        companionField.isAccessible = true
        val instance = companionField.get(null)
        for (
        name in
        listOf(
            "HEADING_REGEX",
            "BULLET_REGEX",
            "IMPLEMENTATION_SECTION_NAMES",
            "ACCEPTANCE_CRITERIA_SECTION_NAMES",
            "DEFINITION_OF_DONE_SECTION_NAMES",
        )
        ) {
            val getter = companionClass.getMethod("get${name.replaceFirstChar { it.titlecase() }}")
            val value = getter.invoke(instance)
            org.junit.jupiter.api.Assertions
                .assertNotNull(value)
        }
    }

    @Test
    fun `normalize infers Technical Task subtasks from Acceptance Criteria`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Upgrade database",
                    "description" to
                        """## 🛠 Problem / Motivation
Upgrade the database.

---

## ✅ Acceptance Criteria
* Schema migration is deployed
* API integration is updated

---""",
                    "issueType" to "Technical Task",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("Schema migration is deployed", subtasks[0]["summary"])
    }

    @Test
    fun `normalize leaves subtasks empty for a single atomic criterion`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Small change",
                    "description" to "## ✅ Acceptance Criteria\n- The label is renamed",
                    "issueType" to "Story",
                    "subtasks" to listOf("invalid entry"),
                ),
            )

        assertEquals(emptyList<Map<String, Any?>>(), result["subtasks"])
    }

    @Test
    fun `normalize limits inferred subtasks and cleans markdown from their summaries`() {
        val items = (1..7).joinToString("\n") { "- **Work package $it** with `code`" }
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Large task",
                    "description" to "## 📋 Implementation Plan\n$items",
                    "issueType" to "Technical Task",
                    "labels" to listOf("valid", 42),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(5, subtasks.size)
        assertEquals("Work package 1 with code", subtasks[0]["summary"])
        assertEquals(listOf("valid"), subtasks[0]["labels"])
        assertTrue((subtasks[0]["description"] as String).contains("## 🔗 Parent Ticket"))
    }

    @Test
    fun `normalize handles missing description unsupported issue type and absent subtasks`() {
        val result = normalizer.normalize(mapOf("summary" to "Unknown", "issueType" to "Epic"))

        assertEquals("", result["description"])
        assertEquals(emptyList<Map<String, Any?>>(), result["subtasks"])
    }

    @Test
    fun `inferSubtasks defaults issueType to Story when value is not a String`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Story default",
                    "description" to
                        """## ✅ Acceptance Criteria
- First criterion
- Second criterion""",
                    "issueType" to 99,
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("Subtask", subtasks[0]["issueType"])
    }

    @Test
    fun `inferSubtasks uses Story fallback when issueType key is missing`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Issue type missing",
                    "description" to
                        """## ✅ Acceptance Criteria
- Step A
- Step B""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
    }

    @Test
    fun `inferSubtasks picks acceptance criteria for Bug issue type`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Fix bug",
                    "issueType" to "Bug",
                    "description" to
                        """## ✅ Acceptance Criteria
- Bug is fixed
- Regression test is added""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertEquals("Bug is fixed", subtasks[0]["summary"])
    }

    @Test
    fun `inferSubtasks falls through when issue type is unsupported and no matching section exists`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Epic",
                    "issueType" to "Epic",
                    "description" to "## Random heading\nSome body text.",
                ),
            )

        assertEquals(emptyList<Map<String, Any?>>(), result["subtasks"])
    }

    @Test
    fun `inferSubtasks uses provided non-blank priority`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Priority test",
                    "issueType" to "Story",
                    "priority" to "Critical",
                    "description" to
                        """## ✅ Acceptance Criteria
- One
- Two""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals("Critical", subtasks[0]["priority"])
    }

    @Test
    fun `inferSubtasks defaults priority to Minor when key is missing`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "No priority",
                    "issueType" to "Story",
                    "description" to
                        """## ✅ Acceptance Criteria
- A
- B""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals("Minor", subtasks[0]["priority"])
    }

    @Test
    fun `removeInternalSubtaskNote keeps trailing content after the note with no blank line`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "X",
                    "description" to
                        """## Description

> **Note:** This is an in-description section
> spanning two lines.
This line is NOT a quote and is NOT blank""",
                ),
            )

        assertTrue((result["description"] as String).contains("This line is NOT a quote"))
        assertFalse((result["description"] as String).contains("in-description section"))
    }

    @Test
    fun `removeInternalSubtaskNote handles note that reaches end of description`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "X",
                    "description" to
                        """## Description

> **Note:** This is an in-description section
> spanning two lines""",
                ),
            )

        assertFalse((result["description"] as String).contains("in-description section"))
    }

    @Test
    fun `extractSection breaks out at the second heading once the first matches`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Two sections",
                    "description" to
                        """## 🛠 Objective
Setup.

---

## 📋 Steps / Sub-tasks
- Step one
- Step two

---

## Next section
ignored""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
    }

    @Test
    fun `extractSection skips empty bullet entries`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Empty bullets",
                    "issueType" to "Story",
                    "description" to
                        """## ✅ Acceptance Criteria
- Real bullet
-   
- Another bullet""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
    }

    @Test
    fun `extractFirstSection returns empty string when no other heading exists`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Single section",
                    "issueType" to "Story",
                    "description" to "## ✅ Acceptance Criteria\n- A\n- B",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertTrue((subtasks[0]["description"] as String).contains("## 🔗 Parent Ticket"))
    }

    @Test
    fun `extractFirstSection stops at a horizontal rule marker inside the parent context`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "HR marker",
                    "issueType" to "Story",
                    "description" to
                        """## 🛠 Objective
This is the parent context.

---

## ✅ Acceptance Criteria
- Criterion 1
- Criterion 2""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertTrue((subtasks[0]["description"] as String).contains("This is the parent context."))
    }

    @Test
    fun `extractFirstSection skips lines starting with quote character`() {
        val result =
            normalizer.normalize(
                mapOf(
                    "summary" to "Quote handling",
                    "issueType" to "Story",
                    "description" to
                        """## 🛠 Objective
> blockquote line (skipped)

plain line (kept)

---

## ✅ Acceptance Criteria
- Criterion A
- Criterion B""",
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val subtasks = result["subtasks"] as List<Map<String, Any?>>
        assertEquals(2, subtasks.size)
        assertTrue((subtasks[0]["description"] as String).contains("plain line (kept)"))
        assertFalse((subtasks[0]["description"] as String).contains("blockquote line"))
    }
}
