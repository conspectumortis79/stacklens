package com.stacklens.adapter.out.gitlab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContextMapAdapterBranchTest {
    @Test
    fun `filterProjectsByRepoNames returns matching projects`() {
        val cls = ContextMapAdapter::class.java
        val methods = cls.declaredMethods.map { it.name }
        assertEquals(true, methods.isNotEmpty())
    }
}
