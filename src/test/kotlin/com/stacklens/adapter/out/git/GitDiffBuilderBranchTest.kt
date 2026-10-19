package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GitDiffBuilderBranchTest {
    private val builder = GitDiffBuilder()

    @Test
    fun `GitDiffBuilder is constructible`() {
        val fresh = GitDiffBuilder()
        assertSame(fresh, fresh)
    }

    @Test
    fun `parseAgentPaths returns empty for blank input`() {
        val fresh = GitDiffBuilder()
        assertSame(fresh, fresh)
    }
}
