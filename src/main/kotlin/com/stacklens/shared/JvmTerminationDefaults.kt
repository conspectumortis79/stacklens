package com.stacklens.shared

val haltJvm: (Int) -> Unit = { code -> Runtime.getRuntime().halt(code) }

val haltImmediately: () -> Unit = { Runtime.getRuntime().halt(0) }

val exitJvm: (Int) -> Unit = { code -> System.exit(code) }
