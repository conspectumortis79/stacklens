package com.stacklens.desktop

internal fun Any.jsSetMember(
    name: String,
    value: Any,
) {
    javaClass.getMethod("setMember", String::class.java, Any::class.java).invoke(this, name, value)
}
