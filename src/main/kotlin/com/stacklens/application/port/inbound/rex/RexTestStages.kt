package com.stacklens.application.port.inbound.rex

object RexTestStages {
    val DEFAULT: List<RexTestStageSpec> =
        listOf(
            RexTestStageSpec(
                stepLabel = "unit-test",
                targetAliases = arrayOf("unit-test", "test-unit", "test"),
                noTargetMsg = "copilot_pipeline.rex_no_unit_test_target",
                runningMsgTemplate = "copilot_pipeline.rex_running_unit_tests",
                fileExtensionRegex = Regex("""[\w./\-]+\.php"""),
                task = "Fix failing PHPUnit tests. Prefer fixing the test assertions unless the implementation is clearly wrong.",
                fixAttemptMsgKey = "copilot_pipeline.rex_ai_fix_test_attempt",
                successMsgTemplate = "✅ `make \$target` erfolgreich nach KI-Fix (Versuch \$attempt)",
            ),
            RexTestStageSpec(
                stepLabel = "integration-test",
                targetAliases = arrayOf("integration-test", "test-integration"),
                noTargetMsg = "copilot_pipeline.rex_no_integration_test_target",
                runningMsgTemplate = "copilot_pipeline.rex_running_integration_tests",
                fileExtensionRegex = Regex("""[\w./\-]+\.php"""),
                task = "Fix failing integration tests. Prefer fixing the test setup or assertions unless the implementation is clearly wrong.",
                fixAttemptMsgKey = "copilot_pipeline.rex_ai_fix_integration_test_attempt",
                successMsgTemplate = "✅ `make \$target` erfolgreich nach KI-Fix (Versuch \$attempt)",
            ),
            RexTestStageSpec(
                stepLabel = "system-test",
                targetAliases = arrayOf("system-test", "test-system", "e2e", "e2e-test"),
                noTargetMsg = "copilot_pipeline.rex_no_system_test_target",
                runningMsgTemplate = "copilot_pipeline.rex_running_system_tests",
                fileExtensionRegex = Regex("""[\w./\-]+\.(?:php|spec\.[tj]s|test\.[tj]s)"""),
                task = "Fix failing system/integration tests. Prefer fixing the test setup unless the implementation is clearly wrong.",
                fixAttemptMsgKey = "copilot_pipeline.rex_ai_fix_system_test_attempt",
                successMsgTemplate = "✅ `make \$target` erfolgreich nach KI-Fix (Versuch \$attempt)",
            ),
        )
}
