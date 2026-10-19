package com.stacklens.shared

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

fun InputStream.utf8Reader(): BufferedReader = BufferedReader(InputStreamReader(this, StandardCharsets.UTF_8))
