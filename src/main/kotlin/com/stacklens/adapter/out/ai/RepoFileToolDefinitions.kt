package com.stacklens.adapter.out.ai

object RepoFileToolDefinitions {
    private val listProperties: Map<String, Any> =
        mapOf(
            "path" to
                mapOf(
                    "type" to "string",
                    "description" to
                        "Relative directory path within the repository. Use \"\" or \".\" for the repo root.",
                ),
            "recursive" to
                mapOf(
                    "type" to "boolean",
                    "description" to "If true, list files in all sub-directories. Default: false.",
                ),
        )

    private val listParams: Map<String, Any> =
        mapOf("type" to "object", "properties" to listProperties, "required" to listOf("path"))

    private val listFunction: Map<String, Any> =
        mapOf(
            "name" to "list",
            "description" to
                "List the files and directories inside a directory of the repository. " +
                "Always call this BEFORE delete/read on directories whose exact file names you do not know. " +
                "Excludes vendor/, node_modules/, .git/, build/. Returns one path per line, suffixed with '/' for directories.",
            "parameters" to listParams,
        )

    private val readProperties: Map<String, Any> =
        mapOf("path" to mapOf("type" to "string", "description" to "Relative path within the repository"))

    private val readParams: Map<String, Any> =
        mapOf("type" to "object", "properties" to readProperties, "required" to listOf("path"))

    private val readFunction: Map<String, Any> =
        mapOf(
            "name" to "read",
            "description" to
                "Read the full content of a file from the repository. " +
                "Returns the file content as a string, or an error if the file does not exist.",
            "parameters" to readParams,
        )

    private val writeProperties: Map<String, Any> =
        mapOf(
            "path" to mapOf("type" to "string", "description" to "Relative path within the repository"),
            "content" to mapOf("type" to "string", "description" to "Full file content to write"),
        )

    private val writeParams: Map<String, Any> =
        mapOf("type" to "object", "properties" to writeProperties, "required" to listOf("path", "content"))

    private val writeFunction: Map<String, Any> =
        mapOf(
            "name" to "write",
            "description" to
                "Create or overwrite a file in the repository with the given content. " +
                "Parent directories are created automatically.",
            "parameters" to writeParams,
        )

    private val deleteProperties: Map<String, Any> =
        mapOf("path" to mapOf("type" to "string", "description" to "Relative path within the repository"))

    private val deleteParams: Map<String, Any> =
        mapOf("type" to "object", "properties" to deleteProperties, "required" to listOf("path"))

    private val deleteFunction: Map<String, Any> =
        mapOf(
            "name" to "delete",
            "description" to "Delete a file from the repository.",
            "parameters" to deleteParams,
        )

    private val functions: List<Map<String, Any>> = listOf(listFunction, readFunction, writeFunction, deleteFunction)

    val DEFINITIONS: List<Map<String, Any>> = functions.map { mapOf("type" to "function", "function" to it) }

    val ANTHROPIC_DEFINITIONS: List<Map<String, Any>> =
        functions.map { fn ->
            mapOf(
                "name" to fn.getValue("name"),
                "description" to fn.getValue("description"),
                "input_schema" to fn.getValue("parameters"),
            )
        }
}
