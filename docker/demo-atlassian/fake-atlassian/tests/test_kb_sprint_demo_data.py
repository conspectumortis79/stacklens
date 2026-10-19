"""
Smoke test for the StackLens fake-atlassian demo data (Wissensdatenbank
Sprint-Import feature).

Verifies that the demo Jira/Sprint-Import data carries the records that
the StackLens Wissensdatenbank (RAG) → Inhalt → Sprint badge depends on:

  1. KBDEMO-0..3 exist in [JIRA_ISSUES] so the JiraHierarchyAdapter can
     fetch each one via /rest/api/{2,3}/issue/<key>.

  2. KBDEMO-0 has a `customfield_10020` entry pointing at Sprint 44
     ("Sprint Demo KB-Import"). Without this the StackLens sprint-name
     lookup — which runs a `sprint = "Sprint Demo KB-Import"` JQL and
     then scans every issue's fields for `{id, name, state, boardId}`
     sprint objects — returns zero candidates.

  3. KBDEMO-1..3 each carry Sprint 44 membership so the
     JiraSprintIssueKeyLister's JQL `sprint = 44 AND issuetype not in
     subTaskIssueTypes()` finds the real demo tickets, not just the
     carrier.

  4. SPRINT_BOARDS[5] contains Sprint 44 with the expected name + state.

  5. SPRINT_ISSUES[44] is populated with the three demo tickets so the
     Agile-API /rest/agile/1.0/sprint/44/issue endpoint is usable as a
     fallback.

  6. _search_jira recognises the two new JQL clauses:
     - `sprint = N` (numeric) — filters by customfield_10020
     - `issuetype not in subTaskIssueTypes()` — excludes sub-tasks

Plain-Python / unittest — no extra build dependency.
"""

from __future__ import annotations

import ast
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import app  # noqa: E402
import data  # noqa: E402


SPRINT_44_ID = 44
SPRINT_44_NAME = "Sprint Demo KB-Import"
KBDEMO_KEYS = ["KBDEMO-0", "KBDEMO-1", "KBDEMO-2", "KBDEMO-3"]


def _by_key(issue_list):
    return {i["key"]: i for i in issue_list}


class KbSprintImportDemoDataTest(unittest.TestCase):
    def setUp(self) -> None:
        ast.parse((HERE.parent / "data.py").read_text())
        ast.parse((HERE.parent / "app.py").read_text())

    def test_kbdemo_tickets_exist_in_jira_issues(self) -> None:
        by_key = _by_key(data.JIRA_ISSUES)
        for key in KBDEMO_KEYS:
            self.assertIn(key, by_key, f"{key} must exist in JIRA_ISSUES")

    def test_kbdemo_0_carries_sprint_44_membership(self) -> None:
        carrier = _by_key(data.JIRA_ISSUES)["KBDEMO-0"]
        membership = carrier["fields"].get("customfield_10020")
        self.assertIsNotNone(membership)
        target = next(
            (s for s in membership if s.get("id") == SPRINT_44_ID),
            None,
        )
        self.assertIsNotNone(
            target,
            f"KBDEMO-0 customfield_10020 must contain an entry with id={SPRINT_44_ID}",
        )
        self.assertEqual(SPRINT_44_NAME, target.get("name"))
        self.assertEqual("active", target.get("state"))
        self.assertEqual(5, target.get("boardId"))

    def test_kbdemo_1_2_3_each_carry_sprint_44_membership(self) -> None:
        by_key = _by_key(data.JIRA_ISSUES)
        for key in ("KBDEMO-1", "KBDEMO-2", "KBDEMO-3"):
            membership = by_key[key]["fields"].get("customfield_10020")
            self.assertIsNotNone(membership, f"{key} must expose customfield_10020")
            target = next(
                (s for s in membership if s.get("id") == SPRINT_44_ID),
                None,
            )
            self.assertIsNotNone(
                target,
                f"{key} customfield_10020 must contain an entry with id={SPRINT_44_ID}",
            )
            self.assertEqual(SPRINT_44_NAME, target.get("name"))

    def test_kbdemo_ticket_types_cover_diverse_issue_types(self) -> None:
        by_key = _by_key(data.JIRA_ISSUES)
        types = {by_key[k]["fields"]["issuetype"]["name"] for k in KBDEMO_KEYS}
        self.assertIn("Story", types)
        self.assertIn("Bug", types)
        self.assertIn("Technical Task", types)

    def test_sprint_boards_contain_sprint_44(self) -> None:
        all_sprints = [sprint for sprints in data.SPRINT_BOARDS.values() for sprint in sprints]
        target = next(
            (s for s in all_sprints if s.get("id") == SPRINT_44_ID),
            None,
        )
        self.assertIsNotNone(target)
        self.assertEqual(SPRINT_44_NAME, target.get("name"))
        self.assertEqual("active", target.get("state"))

    def test_sprint_44_issues_contains_kbdemo_1_to_3(self) -> None:
        sprint_44_issues = data.SPRINT_ISSUES.get(SPRINT_44_ID, [])
        keys = {i["key"] for i in sprint_44_issues}
        for expected in ("KBDEMO-1", "KBDEMO-2", "KBDEMO-3"):
            self.assertIn(expected, keys)

    def test_search_jira_sprint_numeric_filter(self) -> None:
        result = app._search_jira("sprint = 44", max_results=50)
        keys = {i["key"] for i in result["issues"]}
        self.assertIn("KBDEMO-1", keys)
        self.assertIn("KBDEMO-2", keys)
        self.assertIn("KBDEMO-3", keys)
        self.assertNotIn("DEMO-1", keys, "Sprint 42 tickets must not leak into Sprint 44 results")
        self.assertNotIn("SPRINT-201", keys, "Sprint 43 tickets must not leak into Sprint 44 results")

    def test_search_jira_sprint_44_with_subtask_exclusion(self) -> None:
        result = app._search_jira(
            "sprint = 44 AND issuetype not in subTaskIssueTypes()",
            max_results=50,
        )
        keys = {i["key"] for i in result["issues"]}
        self.assertIn("KBDEMO-1", keys)
        self.assertIn("KBDEMO-2", keys)
        self.assertIn("KBDEMO-3", keys)
        for issue in result["issues"]:
            issue_type = issue["fields"].get("issuetype", {}).get("name", "")
            self.assertNotIn(
                issue_type.lower(),
                ("sub-task", "subtask", "teilaufgabe"),
                f"{issue['key']} is a sub-task and must be excluded",
            )

    def test_search_jira_sprint_42_filter_still_works(self) -> None:
        # Sprint 42 only carries the DEMO-100 carrier via customfield_10020
        # in JIRA_ISSUES — the DEMO-1..16 children live in [SPRINT_ISSUES][42]
        # only (the legacy Agile-API path used by the Sprint-Review feature).
        # The JQL walk therefore returns DEMO-100 but not DEMO-1..16.
        result = app._search_jira(
            "sprint = 42 AND issuetype not in subTaskIssueTypes()",
            max_results=50,
        )
        keys = {i["key"] for i in result["issues"]}
        self.assertIn("DEMO-100", keys)
        self.assertNotIn("KBDEMO-1", keys)
        self.assertNotIn("SPRINT-201", keys)

    def test_jql_sprint_id_parser(self) -> None:
        self.assertEqual(44, app._parse_jql_sprint_id("sprint = 44"))
        self.assertEqual(44, app._parse_jql_sprint_id("sprint=44 AND issuetype not in subTaskIssueTypes()"))
        self.assertIsNone(app._parse_jql_sprint_id(""))
        self.assertIsNone(app._parse_jql_sprint_id('sprint = "Sprint Demo KB-Import"'))

    def test_jql_exclude_subtasks_parser(self) -> None:
        self.assertTrue(
            app._parse_jql_exclude_subtasks("sprint = 44 AND issuetype not in subTaskIssueTypes()"),
        )
        self.assertTrue(
            app._parse_jql_exclude_subtasks("issuetype not in subTaskIssueTypes()"),
        )
        self.assertFalse(app._parse_jql_exclude_subtasks("sprint = 44"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
