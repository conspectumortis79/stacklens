package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MrProviderTest {
    @Test
    fun `github and its subdomains resolve to GitHub`() {
        assertEquals(MrProvider.GITHUB, MrProvider.resolve("github.com", "/o/r/pull/1"))
        assertEquals(MrProvider.GITHUB, MrProvider.resolve("www.github.com", "/o/r/pull/1"))
    }

    @Test
    fun `a merge request path resolves to GitLab for any other host`() {
        assertEquals(MrProvider.GITLAB, MrProvider.resolve("gitlab.example", "/o/r/-/merge_requests/1"))
        assertEquals(MrProvider.GITLAB, MrProvider.resolve("bitbucket.example", "/o/r/-/merge_requests/1"))
    }

    @Test
    fun `an unknown host without a merge request path has no provider`() {
        assertNull(MrProvider.resolve("bitbucket.example", "/o/r/pull/1"))
        assertNull(MrProvider.resolve("gitlab.example", ""))
    }

    @Test
    fun `the github host predicate wins over a merge request path`() {
        assertEquals(MrProvider.GITHUB, MrProvider.resolve("github.com", "/o/r/-/merge_requests/1"))
    }
}
