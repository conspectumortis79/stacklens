package com.stacklens.application.service

import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.codeanalysis.CodeAnalysisCancellationService
import com.stacklens.application.service.codeanalysis.CodeAnalysisExecutor
import com.stacklens.application.service.codeanalysis.CodeAnalysisMrService
import com.stacklens.application.service.codeanalysis.MonorepoDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaGetter

class CodeAnalysisServiceDepsTest {
    @Test
    fun `every CodeAnalysisServiceDeps field has a matching accessor on the service`() {
        val depsFieldNames: Set<String> =
            CodeAnalysisServiceDeps::class
                .declaredMemberProperties
                .map { it.name }
                .toSet()

        val accessorNames: Set<String> =
            CodeAnalysisService::class
                .declaredMemberProperties
                .filter { it.javaGetter != null }
                .map { it.name }
                .toSet()

        assertTrue(
            depsFieldNames.isNotEmpty(),
            "CodeAnalysisServiceDeps must declare at least one field for this contract to be meaningful",
        )

        depsFieldNames.forEach { fieldName ->
            assertTrue(
                accessorNames.contains(fieldName),
                "CodeAnalysisService is missing a `private val `$fieldName` get() = deps.`$fieldName` accessor. " +
                    "Either add the accessor or remove `$fieldName` from CodeAnalysisServiceDeps " +
                    "(in which case the body of the service must use `deps.`$fieldName`).",
            )
        }

        val stale = accessorNames - depsFieldNames
        assertEquals(
            emptySet<String>(),
            stale,
            "CodeAnalysisService declares accessors for fields that no longer live on " +
                "CodeAnalysisServiceDeps: $stale. Either remove the accessor or restore the deps field.",
        )
    }

    @Test
    fun `dependency bundle wires up correctly`() {
        CodeAnalysisService(
            CodeAnalysisServiceDeps(
                analysisRepo = mock<CodeAnalysisPort>(),
                codeRepoPort = mock<CodeAnalysisRepoApi>(),
                copilotCliService = mock<CopilotPort>(),
                i18n = mock<I18nPort>(),
                startupHealer = mock<StartupRecovery>(),
                monorepoDetector = mock<MonorepoDetector>(),
                mrService = mock<CodeAnalysisMrService>(),
                asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                cancellationService = mock<CodeAnalysisCancellationService>(),
            ),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            executor = mock<CodeAnalysisExecutor>(),
        )
    }
}
