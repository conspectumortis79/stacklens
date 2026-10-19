package com.stacklens.adapter.out.docker

const val TESTDOCKER_PROJECT_NAME: String = "stacklens-testdocker"

fun testdockerProjectFlag(): List<String> = listOf("-p", TESTDOCKER_PROJECT_NAME)
