package com.stacklens.testsupport

import com.stacklens.application.port.out.platform.HostDirectoryPort

class FakeHostDirectoryPort(
    private val home: String = "/home/tester",
    private val working: String = "/home/tester/project",
    private val temporary: String = "/tmp/stacklens-test",
) : HostDirectoryPort {
    override fun homeDirectory(): String = home

    override fun workingDirectory(): String = working

    override fun temporaryDirectory(): String = temporary
}
