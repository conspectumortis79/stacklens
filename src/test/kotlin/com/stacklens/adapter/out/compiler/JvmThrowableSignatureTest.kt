package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JvmThrowableSignatureTest {
    @Test
    fun `empty output is not a throwable`() {
        assertFalse(JvmThrowableSignature.looksLikeThrowable(""))
    }

    @Test
    fun `output without a stack frame is not a throwable`() {
        assertFalse(JvmThrowableSignature.looksLikeThrowable("java.lang.IllegalStateException: boom without frame"))
    }

    @Test
    fun `stack frame without a known exception type is not a throwable`() {
        assertFalse(JvmThrowableSignature.looksLikeThrowable("com.example.Boom: failed\n\tat com.example.Main.main(Main.kt:1)"))
    }

    @Test
    fun `stack frame whose first line has no message is not a throwable`() {
        assertFalse(JvmThrowableSignature.looksLikeThrowable("java.lang.IllegalStateException\n\tat com.example.Main.main(Main.kt:1)"))
    }

    @Test
    fun `uncaught exception marker makes the output a throwable even without a known prefix`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("Exception in thread \"AWT-EventQueue-0\" com.example.Boom\n\tat x"))
    }

    @Test
    fun `recognises java exception prefix`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("java.lang.RuntimeException: boom\n\tat x"))
    }

    @Test
    fun `recognises javax exception prefix`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("javax.swing.UnsupportedLookAndFeelException: boom\n\tat x"))
    }

    @Test
    fun `recognises kotlin exception prefix`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("kotlin.KotlinNullPointerException: boom\n\tat x"))
    }

    @Test
    fun `recognises jetbrains kotlin exception prefix`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("org.jetbrains.kotlin.KotlinNodeJsException: boom\n\tat x"))
    }

    @Test
    fun `recognises kotlinx exception prefix`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("kotlinx.coroutines.JobCancellationException: boom\n\tat x"))
    }

    @Test
    fun `recognises a throwable signature without a newline`() {
        assertTrue(JvmThrowableSignature.looksLikeThrowable("java.lang.RuntimeException: boom\tat com.example.Main.main"))
    }
}
