package com.stacklens.adapter.out.docker

const val COMPOSE_SUBCOMMAND: String = "compose"

fun testdockerComposeArgs(
    composeFile: String,
    vararg composeArgs: String,
): List<String> =
    listOf(
        COMPOSE_SUBCOMMAND,
        *testdockerProjectFlag().toTypedArray(),
        "-f",
        composeFile,
        *composeArgs,
    )

fun testdockerComposeCommand(
    dockerExecutable: String,
    composeFile: String,
    vararg composeArgs: String,
): List<String> = listOf(dockerExecutable) + testdockerComposeArgs(composeFile, *composeArgs)
