package com.stacklens.shared

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

fun OutputStream.utf8Writer(): BufferedWriter = BufferedWriter(OutputStreamWriter(this, StandardCharsets.UTF_8))
