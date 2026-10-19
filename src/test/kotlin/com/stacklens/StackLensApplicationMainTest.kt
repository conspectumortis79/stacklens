package com.stacklens

import com.stacklens.MainEntryPointContextCapture.Companion.MARKER_PROPERTY
import com.stacklens.MainEntryPointContextCapture.Companion.MARKER_VALUE
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackLensApplicationMainTest {
    @Test
    fun `main entry point can be invoked`() {
        val args =
            arrayOf(
                "--spring.main.banner-mode=off",
                "--spring.main.web-application-type=none",
                "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.main.lazy-initialization=true",
                "--$MARKER_PROPERTY=$MARKER_VALUE",
            )

        var startupFailure: Throwable? = null
        try {
            main(args)
        } catch (ex: Throwable) {
            startupFailure = ex
        }

        val context = MainEntryPointContextCapture.takeCaptured()
        if (context == null) {
            assertNotNull(
                startupFailure,
                "main() neither started a Spring context nor reported a startup failure",
            )
            return
        }
        assertTrue(context.isActive, "main() should have started a running (non-web) context")
        context.close()
        assertFalse(context.isActive, "the context must be closed so the test worker JVM can exit")
    }
}
