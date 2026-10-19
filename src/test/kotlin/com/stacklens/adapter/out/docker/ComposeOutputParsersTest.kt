package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ComposeOutputParsersTest {
    @Test
    fun `the ps parser reads name state and exit code`() {
        val state = ComposePsParser.parse("api\trunning\t0\nworker\texited\t3\n\nshort\n")

        assertEquals(2, state.entries.size)
        assertEquals("api", state.entries[0].name)
        assertEquals(3, state.entries[1].exitCode)
    }

    @Test
    fun `the ps parser tolerates a non numeric exit code`() {
        val state = ComposePsParser.parse("api\trunning\tnot-a-number\n")

        assertEquals(0, state.entries.single().exitCode)
    }

    @Test
    fun `socket candidates always carry the home and system paths`() {
        val candidates = DockerSocketCandidates.of(home = "/home/u", xdg = "/run/user/1000", uid = "1000")

        assertEquals(
            listOf(
                "/home/u/.docker/run/docker.sock",
                "/home/u/Library/Containers/com.docker.docker/Data/docker.sock",
                "/home/u/.docker/desktop/docker.sock",
                "/var/run/docker.sock",
                "/run/user/1000/docker.sock",
                "/run/user/1000/docker.sock",
            ),
            candidates,
        )
    }

    @Test
    fun `socket candidates skip the optional paths when they are blank`() {
        val candidates = DockerSocketCandidates.of(home = "/home/u", xdg = " ", uid = null)

        assertEquals(4, candidates.size)
        assertEquals(DockerSocketCandidates.SYSTEM_SOCKET, candidates[3])
    }

    @Test
    fun `proc status extracts the uid line`() {
        val file = Files.createTempFile("stacklens-proc-status", ".txt").toFile()
        file.writeText("Name:\tcat\nUid:\t1000\t1000\t1000\t1000\n")

        val (uid, line) = ProcSelfStatus.uidOf(file)

        assertEquals("1000", uid)
        assertEquals("Uid:\t1000\t1000\t1000\t1000", line)

        file.delete()
    }

    @Test
    fun `proc status stays quiet for a missing file`() {
        val (uid, line) = ProcSelfStatus.uidOf(File("/proc/self/definitely-not-here"))

        assertNull(uid)
        assertNull(line)
    }
}
