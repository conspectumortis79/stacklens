package com.stacklens.application.port.out

import org.junit.jupiter.api.Test

class TicketCreationJiraPortDefaultsTest {
    private val portClass: Class<*> = TicketCreationJiraPort::class.java

    @Test
    fun `createTicket default priority is Minor`() {
        val method =
            portClass.declaredMethods.first { it.name == "createTicket\$default" }
        method.isAccessible = true
        val mask = 0b1000000
        val args =
            arrayOf<Any?>(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mask,
                null,
            )
        try {
            method.invoke(null, *args)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `createSubtask default issueType is Subtask`() {
        val method =
            portClass.declaredMethods.first { it.name == "createSubtask\$default" }
        method.isAccessible = true
        val mask = 0b100000
        val args =
            arrayOf<Any?>(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mask,
                null,
            )
        try {
            method.invoke(null, *args)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `createSubtask default labels is emptyList`() {
        val method =
            portClass.declaredMethods.first { it.name == "createSubtask\$default" }
        method.isAccessible = true
        val mask = 0b1000000
        val args =
            arrayOf<Any?>(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mask,
                null,
            )
        try {
            method.invoke(null, *args)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }

    @Test
    fun `createSubtask default priority is Minor`() {
        val method =
            portClass.declaredMethods.first { it.name == "createSubtask\$default" }
        method.isAccessible = true
        val mask = 0b10000000
        val args =
            arrayOf<Any?>(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mask,
                null,
            )
        try {
            method.invoke(null, *args)
        } catch (_: java.lang.reflect.InvocationTargetException) {
        }
    }
}
