package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.service.FakeI18n
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

class CopilotModelsConnectionTesterParseModelsCoverageTest {
    private val tester = CopilotModelsConnectionTester(RestTemplate(), FakeI18n())

    @Test
    fun `an enabled model without an identifier is listed with an empty id`() {
        val models = parse("""{"data":[{"model_picker_enabled":true,"name":"named"}]}""")

        assertEquals(listOf(mapOf("id" to "", "name" to "named")), models)
    }

    @Test
    fun `an enabled model without a display name falls back to its identifier`() {
        val models = parse("""{"data":[{"model_picker_enabled":true,"id":"real-id"}]}""")

        assertEquals(listOf(mapOf("id" to "real-id", "name" to "real-id")), models)
    }

    @Test
    fun `a missing data node yields no models`() {
        assertEquals(emptyList<Map<String, String>>(), parse("""{"meta":{}}"""))
    }

    private fun parse(json: String): Any? = PrivateInvocation.invoke(tester, "parseModels", ObjectMapper().readTree(json) as JsonNode)
}
