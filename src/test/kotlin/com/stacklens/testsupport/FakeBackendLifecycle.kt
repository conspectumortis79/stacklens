package com.stacklens.testsupport

import com.stacklens.desktop.BackendLifecycle

class FakeBackendLifecycle(
    private val port: Int = 8080,
) : BackendLifecycle {
    var starts: Int = 0
        private set
    var stops: Int = 0
        private set
    var checkpoints: Int = 0
        private set

    override fun start(): Int {
        starts++
        return port
    }

    override fun stop() {
        stops++
    }

    override fun checkpoint() {
        checkpoints++
    }
}
