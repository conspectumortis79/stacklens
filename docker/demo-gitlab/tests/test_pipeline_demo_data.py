"""
Smoke test for the StackLens fake-gitlab demo data.

Verifies that the demo always ships with the project the Pipeline Analysis
feature relies on (`demo-group/pipeline-demo`) and that every expected
compose / pipeline file is present and syntactically reasonable.

This is intentionally a plain Python script — no test framework dependency —
so it can be run inside the Docker image build or on the host without any
extra setup.
"""

from __future__ import annotations

import ast
import sys
import unittest
from pathlib import Path

# Make `data.py` importable when this script is run from anywhere in the repo.
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "fake-gitlab"))

import data  # noqa: E402


EXPECTED_PROJECT_PATH = "demo-group/pipeline-demo"
EXPECTED_PROJECT_ID = 8

# Files the Pipeline Analysis feature must be able to discover and read.
EXPECTED_FILES = [
    "docker-compose.yml",
    "docker-compose.override.yml",
    "Dockerfile",
    ".gitlab-ci.yml",
    ".github/workflows/ci.yml",
    "Jenkinsfile",
    "bitbucket-pipelines.yml",
    "azure-pipelines.yml",
    ".travis.yml",
    "nginx.conf",
    "package.json",
    "README.md",
]


class PipelineDemoProjectTest(unittest.TestCase):
    def setUp(self) -> None:
        # Fail loudly if data.py stops importing (e.g. due to a syntax error).
        ast.parse((HERE.parent / "fake-gitlab" / "data.py").read_text())

    def test_project_is_registered(self) -> None:
        ids = {p["id"]: p for p in data.PROJECTS}
        self.assertIn(EXPECTED_PROJECT_ID, ids)
        project = ids[EXPECTED_PROJECT_ID]
        self.assertEqual(EXPECTED_PROJECT_PATH, project["path_with_namespace"])
        self.assertEqual("main", project["default_branch"])
        self.assertTrue(project["active"] if "active" in project else True,
                        "Demo project must be active by default")

    def test_project_has_all_expected_files(self) -> None:
        files = data.PROJECT_FILES.get(EXPECTED_PROJECT_ID, {})
        missing = sorted(set(EXPECTED_FILES) - set(files.keys()))
        self.assertFalse(missing, f"Missing demo files for pipeline-demo: {missing}")

    def test_docker_compose_contains_a_services_block(self) -> None:
        content = data.PROJECT_FILES[EXPECTED_PROJECT_ID]["docker-compose.yml"]
        self.assertIn("services:", content)
        # Postgres + Redis + App + Nginx is the minimum reference stack.
        for service in ("postgres", "redis", "app", "nginx"):
            self.assertIn(f"  {service}:", content,
                          f"docker-compose.yml must define service '{service}'")

    def test_gitlab_ci_defines_stages(self) -> None:
        content = data.PROJECT_FILES[EXPECTED_PROJECT_ID][".gitlab-ci.yml"]
        self.assertIn("stages:", content)
        # The Pipeline Analysis prompt explicitly mentions these stages.
        for stage in ("validate", "test", "build", "deploy"):
            self.assertIn(f"  - {stage}", content,
                          f".gitlab-ci.yml must include stage '{stage}'")

    def test_github_actions_workflow_runs_on_push(self) -> None:
        content = data.PROJECT_FILES[EXPECTED_PROJECT_ID][".github/workflows/ci.yml"]
        self.assertIn("on:", content)
        self.assertIn("push:", content)
        # Must contain at least one job (the Pipeline Analysis would otherwise
        # see no jobs to report).
        self.assertIn("jobs:", content)

    def test_jenkinsfile_declares_a_pipeline_block(self) -> None:
        content = data.PROJECT_FILES[EXPECTED_PROJECT_ID]["Jenkinsfile"]
        self.assertIn("pipeline {", content)
        self.assertIn("stages {", content)

    def test_files_are_non_empty(self) -> None:
        files = data.PROJECT_FILES[EXPECTED_PROJECT_ID]
        for path, content in files.items():
            self.assertTrue(content and content.strip(),
                            f"Demo file '{path}' must not be empty")


if __name__ == "__main__":
    unittest.main(verbosity=2)
