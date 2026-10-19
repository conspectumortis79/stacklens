package com.stacklens.coverage

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PrivateCompanionInitializationTest {
    @ParameterizedTest(name = "companion singleton of {0} is initialised with readable constants")
    @ValueSource(
        strings = [
            "com.stacklens.adapter.inbound.rest.KiChatController",
            "com.stacklens.adapter.out.ai.OpenAiCompatibleConnectionTester",
            "com.stacklens.adapter.out.ai.TesseractNativeLibraryBootstrap",
            "com.stacklens.adapter.out.docker.ComposeContainerStateAdapter",
            "com.stacklens.adapter.out.grafana.GrafanaHttpClient",
            "com.stacklens.adapter.out.knowledge.confluence.ConfluenceCalendarMacroDetector",
            "com.stacklens.adapter.out.textfilter.TomlFilterParser",
            "com.stacklens.adapter.out.textfilter.TomlValueParser",
            "com.stacklens.adapter.out.tooling.FileToolJarStore",
            "com.stacklens.adapter.out.codeqa.IntegrationTestStreamBufferAdapter",
            "com.stacklens.application.service.JiraTicketSummaryService",
            "com.stacklens.application.service.KiChatAttachmentPreparer",
            "com.stacklens.application.service.LocalRepoService",
            "com.stacklens.application.service.TicketRefinementService",
        ],
    )
    fun `companion constants are available after class initialisation`(className: String) {
        val owner = Class.forName(className)
        val companionField = owner.getDeclaredField("Companion")
        val expectedType = companionField.type

        Class.forName(expectedType.name, true, owner.classLoader)

        companionField.isAccessible = true
        val companion = companionField.get(null)
        assertNotNull(companion, "$className keeps its companion singleton")

        expectedType.declaredMethods
            .filter { it.parameterCount == 0 && !it.isSynthetic && it.name.startsWith("get") && it.returnType != Void.TYPE }
            .filter { method -> method.annotations.none { it.annotationClass.simpleName == "Nullable" } }
            .forEach { accessor ->
                accessor.isAccessible = true
                assertNotNull(accessor.invoke(companion), "$expectedType.${accessor.name} returns a value")
            }
    }

    @Test
    fun `the default file probe of the credential store checks the real file system`() {
        val probeClass = Class.forName("com.stacklens.adapter.out.ai.ClaudeCodeCredentialFileStore\$DefaultClaudeCredentialFileProbe")
        val instanceField = probeClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true

        val probe = instanceField.get(null)

        assertNotNull(probe)
        val exists = probeClass.getMethod("exists", java.nio.file.Path::class.java)
        exists.isAccessible = true
        assertTrue(
            exists.invoke(
                probe,
                java.nio.file.Paths
                    .get("/"),
            ) as Boolean,
        )
        assertTrue(
            !(
                exists.invoke(
                    probe,
                    java.nio.file.Paths
                        .get("/definitely/not/here"),
                ) as Boolean
            ),
        )
    }
}
