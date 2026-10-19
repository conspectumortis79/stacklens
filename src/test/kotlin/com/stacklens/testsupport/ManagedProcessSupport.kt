package com.stacklens.testsupport

import com.stacklens.adapter.out.shared.JvmManagedProcess
import com.stacklens.application.port.out.process.ManagedProcess

fun Process.asManagedProcess(): ManagedProcess = JvmManagedProcess(this)
