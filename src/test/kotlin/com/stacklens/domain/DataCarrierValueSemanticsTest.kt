package com.stacklens.domain

import com.stacklens.testsupport.DataCarrierProbe
import com.stacklens.testsupport.PersistentTypes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DataCarrierValueSemanticsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("carriers")
    fun `value semantics hold for every data carrier`(type: Class<*>) {
        val properties = DataCarrierProbe.exercise(type.kotlin)

        assertTrue(properties > 0, type.name)
    }

    companion object {
        private val INVARIANT_CARRIERS =
            setOf(
                "com.stacklens.domain.model.knowledge.KnowledgeRerankConfig",
                "com.stacklens.domain.text.filter.FilterBundle",
                "com.stacklens.application.service.search.SearchRunContext",
                "com.stacklens.application.port.out.JsonTree.ArrayNode",
                "com.stacklens.application.port.out.JsonTree.ObjectNode",
                "com.stacklens.domain.model.PptxSlideDto",
            )

        @JvmStatic
        fun carriers(): List<Class<*>> =
            PersistentTypes
                .classesIn("com.stacklens")
                .filter { runCatching { it.kotlin.isData }.getOrDefault(false) }
                .filterNot { it.name.replace('$', '.') in INVARIANT_CARRIERS }
    }
}
