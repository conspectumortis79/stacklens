"""
Smoke test for the StackLens fake-atlassian demo data.

Verifies that the demo Jira/Sprint-Review data carries the sprint-membership
records that the StackLens Sprint-Review feature depends on:

  1. `SPRINT-200` has a `customfield_10020` array entry pointing at Sprint 43
     ("Sprint Demo Review"). Without this the StackLens sprint-name lookup
     — which runs a `sprint = "Sprint Demo Review"` JQL and then scans every
     issue's fields for `{id, name, state, boardId}` sprint objects — returns
     zero candidates, even though SPRINT_ISSUES[43] is fully populated.

  2. `DEMO-100` carries the equivalent record for Sprint 42
     ("Sprint 12 – Fulfillment Platform") so a manual "Sprint 12…" run also
     resolves.

  3. SPRINT_BOARDS / SPRINT_ISSUES stay internally consistent (the agile
     endpoint /rest/agile/1.0/sprint/{id}/issue is the second hop of the
     Sprint-Review pipeline).

  4. `_search_jira` recognises both `text ~ "keyword"` and `project = "KEY"`
     JQL clauses (the existing baseline) and is structurally ready to accept
     a `sprint = "..."` extension in a follow-up without breaking the
     keyword/project filters.

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


SPRINT_43_ID = 43
SPRINT_43_NAME = "Sprint Demo Review"
SPRINT_42_ID = 42
SPRINT_42_NAME = "Sprint 12 \u2013 Fulfillment Platform"


def _by_key(issue_list):
    return {i["key"]: i for i in issue_list}


class SprintReviewDemoDataTest(unittest.TestCase):
    def setUp(self) -> None:
        # Fail loudly if data.py or app.py stops importing (e.g. due to a
        # syntax error introduced by a manual edit).
        ast.parse((HERE.parent / "data.py").read_text())
        ast.parse((HERE.parent / "app.py").read_text())

    # ── JIRA_ISSUES membership data ───────────────────────────────────────

    def test_sprint_200_carries_sprint_43_membership(self) -> None:
        sprint_200 = _by_key(data.JIRA_ISSUES)["SPRINT-200"]
        membership = sprint_200["fields"].get("customfield_10020")
        self.assertIsNotNone(
            membership,
            "SPRINT-200 must expose a customfield_10020 so that the "
            "StackLens JQL `sprint = \"Sprint Demo Review\"` walk finds it.",
        )
        self.assertIsInstance(membership, list)
        self.assertGreaterEqual(len(membership), 1)

        target = next(
            (s for s in membership if s.get("id") == SPRINT_43_ID),
            None,
        )
        self.assertIsNotNone(
            target,
            f"SPRINT-200 customfield_10020 must contain an entry with id={SPRINT_43_ID}",
        )
        self.assertEqual(SPRINT_43_NAME, target.get("name"))
        self.assertEqual("active", target.get("state"))
        # boardId is optional in the scanner but helps the clickable
        # backlog URL; ensure it is set so the full UX works.
        self.assertEqual(5, target.get("boardId"))

    def test_demo_100_carries_sprint_42_membership(self) -> None:
        demo_100 = _by_key(data.JIRA_ISSUES)["DEMO-100"]
        membership = demo_100["fields"].get("customfield_10020")
        self.assertIsNotNone(
            membership,
            "DEMO-100 must expose a customfield_10020 so that the "
            "StackLens JQL `sprint = \"Sprint 12 \\u2013 Fulfillment Platform\"` "
            "walk finds it.",
        )
        target = next(
            (s for s in membership if s.get("id") == SPRINT_42_ID),
            None,
        )
        self.assertIsNotNone(
            target,
            f"DEMO-100 customfield_10020 must contain an entry with id={SPRINT_42_ID}",
        )
        self.assertEqual(SPRINT_42_NAME, target.get("name"))
        self.assertEqual("closed", target.get("state"))
        self.assertEqual(5, target.get("boardId"))

    # ── Agile API consistency (the second hop of the Sprint-Review flow) ──

    def test_sprint_boards_contain_sprint_42_and_43(self) -> None:
        all_sprints = [sprint for sprints in data.SPRINT_BOARDS.values() for sprint in sprints]
        ids = {sprint["id"] for sprint in all_sprints}
        self.assertIn(SPRINT_42_ID, ids)
        self.assertIn(SPRINT_43_ID, ids)

    def test_sprint_43_target_ticket_is_in_sprint_issues(self) -> None:
        # The auto-fill target `SPRINT-200` is a sentinel separate from the
        # sprint's children. The StackLens pipeline calls
        # /rest/agile/1.0/sprint/43/issue and expects 4 children, plus the
        # separately-resolved target ticket.
        sprint_43_issues = data.SPRINT_ISSUES.get(SPRINT_43_ID, [])
        keys = {i["key"] for i in sprint_43_issues}
        # SPRINT-201..SPRINT-205 are the per-state demo tickets
        for expected in ("SPRINT-201", "SPRINT-202", "SPRINT-203", "SPRINT-204"):
            self.assertIn(expected, keys)
        # ... and SPRINT-200 is the target ticket in JIRA_ISSUES separately.
        self.assertIn("SPRINT-200", _by_key(data.JIRA_ISSUES))

    def test_sprint_42_has_demo_tickets(self) -> None:
        sprint_42_issues = data.SPRINT_ISSUES.get(SPRINT_42_ID, [])
        keys = {i["key"] for i in sprint_42_issues}
        for expected in ("DEMO-1", "DEMO-2", "DEMO-3"):
            self.assertIn(expected, keys)

    # ── _search_jira baseline ────────────────────────────────────────────

    def test_search_jira_keyword_filter(self) -> None:
        result = app._search_jira('text ~ "newsletter"', max_results=10)
        keys = {i["key"] for i in result["issues"]}
        # DEMO-100 mentions newsletter in its description.
        self.assertIn("DEMO-100", keys)

    def test_search_jira_project_filter(self) -> None:
        result = app._search_jira('project = "DEMO"', max_results=100)
        keys = {i["key"] for i in result["issues"]}
        self.assertIn("DEMO-100", keys)
        # Tickets from other projects must not leak through.
        self.assertNotIn("BACKEND-1", keys)

    def test_search_jira_unfiltered_returns_everything(self) -> None:
        result = app._search_jira("", max_results=200)
        self.assertEqual(
            len(data.JIRA_ISSUES), result["total"],
            "Without any JQL clause the mock returns all of JIRA_ISSUES so "
            "the StackLens scan can find the customfield_10020 sprint records "
            "attached to SPRINT-200 / DEMO-100.",
        )

    # ── JQL parser helpers ───────────────────────────────────────────────

    def test_jql_keyword_parser(self) -> None:
        self.assertEqual("foo", app._parse_jql_keyword('text ~ "foo"'))
        self.assertEqual("foo bar", app._parse_jql_keyword("text ~ 'foo bar'"))
        self.assertEqual("", app._parse_jql_keyword("project = X"))

    def test_jql_projects_parser(self) -> None:
        keys = app._parse_jql_projects('project = "DEMO" OR project = "OPS"')
        self.assertEqual(["DEMO", "OPS"], keys)
        keys = app._parse_jql_projects('project IN ("A","B")')
        self.assertEqual(["A", "B"], keys)


if __name__ == "__main__":
    unittest.main(verbosity=2)
