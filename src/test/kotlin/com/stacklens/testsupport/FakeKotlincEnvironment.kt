package com.stacklens.testsupport

import com.stacklens.adapter.out.compiler.KotlincEnvironment

class FakeKotlincEnvironment(
    private val values: Map<String, String> = emptyMap(),
) : KotlincEnvironment {
    override fun get(key: String): String? = values[key]
}
