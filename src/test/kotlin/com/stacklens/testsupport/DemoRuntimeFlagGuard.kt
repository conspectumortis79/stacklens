package com.stacklens.testsupport

import com.stacklens.adapter.out.demo.DemoRuntimeMode
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DemoRuntimeFlagGuard :
    BeforeEachCallback,
    AfterEachCallback {
    private var previous: String? = null

    override fun beforeEach(context: ExtensionContext) {
        previous = System.getProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
    }

    override fun afterEach(context: ExtensionContext) {
        val saved = previous
        if (saved == null) {
            System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        } else {
            System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, saved)
        }
        previous = null
    }
}
