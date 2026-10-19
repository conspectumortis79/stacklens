package com.stacklens.adapter.out.filesystem

import com.stacklens.adapter.out.shared.RepoScanRules
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalRepoScanAdapterPriorityExhaustiveTest {
    private val adapter = LocalRepoScanAdapter()

    @Test
    fun `tier 2 main prefix returns true`() {
        assertTrue(RepoScanRules().isPriorityFile("main.go"))
        assertTrue(RepoScanRules().isPriorityFile("MAIN.kt"))
    }

    @Test
    fun `tier 2 app prefix returns true`() {
        assertTrue(RepoScanRules().isPriorityFile("app.js"))
    }

    @Test
    fun `tier 2 index prefix returns true`() {
        assertTrue(RepoScanRules().isPriorityFile("index.html"))
    }

    @Test
    fun `tier 2 all prefixes false and file does not match any tier returns false`() {
        assertFalse(RepoScanRules().isPriorityFile("notmain.x"))
        assertFalse(RepoScanRules().isPriorityFile("noapp.x"))
    }

    @Test
    fun `tier 2 endsWith gradle kts triggers true branch`() {
        assertTrue(RepoScanRules().isPriorityFile("anything.gradle.kts"))
    }

    @Test
    fun `tier 2 endsWith gradle kts triggers false branch`() {
        assertFalse(RepoScanRules().isPriorityFile("regularfile.txt"))
    }

    @Test
    fun `tier 3 endsWith controller short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xcontroller"))
    }

    @Test
    fun `tier 3 endsWith handler short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xhandler"))
    }

    @Test
    fun `tier 3 endsWith router short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xrouter"))
    }

    @Test
    fun `tier 3 endsWith routes short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xroutes"))
    }

    @Test
    fun `tier 3 endsWith route short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xroute"))
    }

    @Test
    fun `tier 3 endsWith middleware short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xmiddleware"))
    }

    @Test
    fun `tier 3 endsWith service short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xservice"))
    }

    @Test
    fun `tier 3 endsWith repository short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xrepository"))
    }

    @Test
    fun `tier 3 endsWith dao short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xdao"))
    }

    @Test
    fun `tier 3 endsWith model short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xmodel"))
    }

    @Test
    fun `tier 3 endsWith entity short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xentity"))
    }

    @Test
    fun `tier 3 endsWith config short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xconfig"))
    }

    @Test
    fun `tier 3 endsWith security short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xsecurity"))
    }

    @Test
    fun `tier 3 endsWith auth short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("Xauth"))
    }

    @Test
    fun `tier 3 contains login short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("xlogin.kt"))
    }

    @Test
    fun `tier 3 contains session short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("xsession.kt"))
    }

    @Test
    fun `tier 3 contains permission short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("xpermission.kt"))
    }

    @Test
    fun `tier 3 contains guard short-circuits`() {
        assertTrue(RepoScanRules().isPriorityFile("xguard.kt"))
    }

    @Test
    fun `tier 3 all branches false - returns false`() {
        assertFalse(RepoScanRules().isPriorityFile("boringname.xyz"))
    }

    @Test
    fun `tier 3 base without dot still evaluates correctly`() {
        assertTrue(RepoScanRules().isPriorityFile("MyController"))
    }

    @Test
    fun `tier 3 base without dot returns false when no match`() {
        assertFalse(RepoScanRules().isPriorityFile("MyBoringName"))
    }
}
