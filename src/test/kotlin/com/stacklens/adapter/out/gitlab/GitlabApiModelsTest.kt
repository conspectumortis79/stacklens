package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.enums.ResultType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitlabApiModelsTest {
    @Test
    fun `GitlabProject data class covers all members`() {
        val a =
            GitlabProject(
                id = 1L,
                name = "name",
                pathWithNamespace = "ns/p",
                defaultBranch = "main",
                httpUrlToRepo = "https://x",
            )
        val b = a.copy(name = "renamed")
        val c = a.copy(name = "renamed")
        assertEquals(a, a)
        assertEquals(b, c)
        assertEquals(b.hashCode(), c.hashCode())
        assertNotEquals(a, b)
        assertNotNull(a.toString())
    }

    @Test
    fun `GitlabBlobResult data class covers all members`() {
        val a = GitlabBlobResult(projectId = 1L, path = "p", ref = "main", startline = 5, data = "d")
        val b = a.copy(data = "x")
        val c = a.copy(data = "x")
        assertEquals(a, a)
        assertEquals(b, c)
        assertEquals(b.hashCode(), c.hashCode())
        assertNotEquals(a, b)
        assertNotNull(a.toString())
    }

    @Test
    fun `TreeEntry data class covers all members`() {
        val a = TreeEntry(id = "1", name = "n", path = "p", type = "blob")
        val b = a.copy(name = "x")
        val c = a.copy(name = "x")
        assertEquals(a, a)
        assertEquals(b, c)
        assertEquals(b.hashCode(), c.hashCode())
        assertNotEquals(a, b)
        assertNotNull(a.toString())
    }

    @Test
    fun `ParsedContext data class covers all members`() {
        val a = ParsedContext(className = "C", functionName = "f", resultType = ResultType.FUNCTION)
        val b = a.copy(functionName = "g")
        val c = a.copy(functionName = "g")
        assertEquals(a, a)
        assertEquals(b, c)
        assertEquals(b.hashCode(), c.hashCode())
        assertNotEquals(a, b)
        assertNotNull(a.toString())
    }

    @Test
    fun `GitlabProject with only required params uses defaultBranch null default`() {
        val p = GitlabProject(id = 99L, name = "n", pathWithNamespace = "ns/n")
        assertEquals("main", p.defaultBranch)
        assertEquals("", p.httpUrlToRepo)
    }

    @Test
    fun `GitlabBlobResult default constructor uses all defaults`() {
        val b = GitlabBlobResult()
        assertEquals(0L, b.projectId)
        assertEquals("", b.path)
    }

    @Test
    fun `TreeEntry default constructor uses all defaults`() {
        val t = TreeEntry()
        assertEquals("", t.id)
        assertEquals("", t.type)
    }

    @Test
    fun `GitlabProject equals null and per-field branches`() {
        val a = GitlabProject(id = 1L, name = "n", pathWithNamespace = "ns/p", defaultBranch = "main", httpUrlToRepo = "https://x")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(id = 99L))
        assertNotEquals(a, a.copy(name = "X"))
        assertNotEquals(a, a.copy(pathWithNamespace = "X"))
        assertNotEquals(a, a.copy(defaultBranch = null))
        assertNotEquals(a, a.copy(httpUrlToRepo = "X"))
        val withNullBranch = a.copy(defaultBranch = null)
        assertNotEquals(withNullBranch, withNullBranch.copy(defaultBranch = "main"))
    }

    @Test
    fun `GitlabBlobResult equals null and per-field branches`() {
        val a = GitlabBlobResult(projectId = 1L, path = "p", ref = "main", startline = 5, data = "d")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(projectId = 99L))
        assertNotEquals(a, a.copy(path = "X"))
        assertNotEquals(a, a.copy(ref = "X"))
        assertNotEquals(a, a.copy(startline = 99))
        assertNotEquals(a, a.copy(data = "X"))
    }

    @Test
    fun `TreeEntry equals null and per-field branches`() {
        val a = TreeEntry(id = "1", name = "n", path = "p", type = "blob")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(id = "X"))
        assertNotEquals(a, a.copy(name = "X"))
        assertNotEquals(a, a.copy(path = "X"))
        assertNotEquals(a, a.copy(type = "X"))
    }

    @Test
    fun `ParsedContext equals null and per-field branches`() {
        val a = ParsedContext(className = "C", functionName = "f", resultType = ResultType.FUNCTION)
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(className = null))
        assertNotEquals(a, a.copy(functionName = null))
        assertNotEquals(a, a.copy(resultType = ResultType.CLASS))
        val withNulls = ParsedContext(className = null, functionName = null, resultType = ResultType.FUNCTION)
        assertEquals(withNulls, withNulls.copy())
        assertNotEquals(withNulls, withNulls.copy(className = "C"))
        assertNotEquals(withNulls, withNulls.copy(functionName = "f"))
    }

    @Test
    fun `GitlabProject destructuring covers componentN`() {
        val p = GitlabProject(id = 1L, name = "n", pathWithNamespace = "ns/p")
        val (id, name, path, branch, url) = p
        assertEquals(1L, id)
        assertEquals("n", name)
        assertEquals("ns/p", path)
        assertEquals("main", branch)
        assertEquals("", url)
    }

    @Test
    fun `GitlabBlobResult destructuring covers componentN`() {
        val b = GitlabBlobResult(projectId = 1L, path = "p", ref = "main", startline = 5, data = "d")
        val (pid, bpath, ref, start, data) = b
        assertEquals(1L, pid)
        assertEquals("p", bpath)
        assertEquals("main", ref)
        assertEquals(5, start)
        assertEquals("d", data)
    }

    @Test
    fun `TreeEntry destructuring covers componentN`() {
        val t = TreeEntry(id = "1", name = "n", path = "p", type = "blob")
        val (id, name, path, type) = t
        assertEquals("1", id)
        assertEquals("n", name)
        assertEquals("p", path)
        assertEquals("blob", type)
    }

    @Test
    fun `ParsedContext destructuring covers componentN`() {
        val c = ParsedContext(className = "C", functionName = "f", resultType = ResultType.FUNCTION)
        val (cn, fn, rt) = c
        assertEquals("C", cn)
        assertEquals("f", fn)
        assertEquals(ResultType.FUNCTION, rt)
    }

    @Test
    fun `GitlabProject getters cover all property accessors`() {
        val p = GitlabProject(id = 1L, name = "N", pathWithNamespace = "g/n", defaultBranch = "main", httpUrlToRepo = "http://x")
        assertEquals(1L, p.id)
        assertEquals("N", p.name)
        assertEquals("g/n", p.pathWithNamespace)
        assertEquals("main", p.defaultBranch)
        assertEquals("http://x", p.httpUrlToRepo)
    }

    @Test
    fun `GitlabBlobResult getters cover all property accessors`() {
        val b = GitlabBlobResult(projectId = 5L, path = "p", ref = "r", startline = 1, data = "d")
        assertEquals(5L, b.projectId)
        assertEquals("p", b.path)
        assertEquals("r", b.ref)
        assertEquals(1, b.startline)
        assertEquals("d", b.data)
    }

    @Test
    fun `TreeEntry getters cover all property accessors`() {
        val t = TreeEntry(id = "abc", name = "n", path = "p", type = "blob")
        assertEquals("abc", t.id)
        assertEquals("n", t.name)
        assertEquals("p", t.path)
        assertEquals("blob", t.type)
    }

    @Test
    fun `ParsedContext getters cover all property accessors`() {
        val c = ParsedContext(className = "C", functionName = "f", resultType = ResultType.FUNCTION)
        assertEquals("C", c.className)
        assertEquals("f", c.functionName)
        assertEquals(ResultType.FUNCTION, c.resultType)
    }
}

class GitlabTreeApiClientCompanionTest {
    @Test
    fun `CODE_FILE_EXTENSIONS companion getter is non-empty and contains common extensions`() {
        val exts = GitlabTreeApiClient.CODE_FILE_EXTENSIONS
        assertTrue(exts.contains("kt"))
        assertTrue(exts.contains("java"))
        assertTrue(exts.contains("py"))
    }
}
