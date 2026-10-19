package com.stacklens.testsupport

import com.stacklens.application.port.out.process.SleeperPort

class FakeSleeper(
    private val awake: Boolean = true,
) : SleeperPort {
    val requests: MutableList<Long> = mutableListOf()

    override fun sleep(millis: Long): Boolean {
        requests += millis
        return awake
    }
}
