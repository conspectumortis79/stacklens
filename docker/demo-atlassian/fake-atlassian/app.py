"""
Fake Atlassian API Server
Simulates Jira and Confluence REST APIs for StackLens testing.

Serves:
  Jira (Server/DC v2):  GET/POST /rest/api/2/search
  Jira (Cloud v3):      GET/POST /rest/api/3/search
                        POST     /rest/api/3/search/jql
  Confluence (Server):  GET      /rest/api/content/search
                        GET      /rest/api/content/<id>/child/attachment
  Confluence (Cloud):   GET      /wiki/rest/api/content/search
                        GET      /wiki/rest/api/content/<id>/child/attachment

Authentication: any Basic credentials accepted (no validation).
"""

import re
import json
from typing import Optional
from flask import Flask, request, jsonify
from flask_cors import CORS
from data import JIRA_ISSUES, CONFLUENCE_PAGES, SPRINT_BOARDS, SPRINT_ISSUES

app = Flask(__name__)
CORS(app)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _parse_jql_keyword(jql: str) -> str:
    """Return the keyword from  text ~ "keyword"  in a JQL string."""
    m = re.search(r'text\s*~\s*["\']([^"\']+)["\']', jql, re.IGNORECASE)
    return m.group(1).strip() if m else ""


def _parse_jql_projects(jql: str) -> list:
    """Return list of project keys from project = "X" or project IN ("X","Y")."""
    keys = []
    # project = "KEY"
    for m in re.finditer(r'project\s*=\s*["\']([^"\']+)["\']', jql, re.IGNORECASE):
        keys.append(m.group(1).upper())
    # project IN ("KEY1", "KEY2")
    m = re.search(r'project\s+IN\s*\(([^)]+)\)', jql, re.IGNORECASE)
    if m:
        for k in re.findall(r'["\']([^"\']+)["\']', m.group(1)):
            keys.append(k.upper())
    return list(dict.fromkeys(keys))  # deduplicate, preserve order


def _parse_jql_sprint_id(jql: str) -> Optional[int]:
    """Return the numeric sprint id from  sprint = 42  (without quotes) when
    present, or None. Quoted forms (`sprint = "Foo"`) carry the sprint NAME
    — the real Jira API treats `sprint` as a filter alias, the resolver then
    scans every returned issue's `customfield_10020` to match the name. The
    mock follows the same two-hop model: unquoted numbers short-circuit
    straight to a membership filter on `customfield_10020`.
    """
    m = re.search(r'sprint\s*=\s*(\d+)\b', jql, re.IGNORECASE)
    return int(m.group(1)) if m else None


def _parse_jql_exclude_subtasks(jql: str) -> bool:
    """True when the JQL contains  issuetype not in subTaskIssueTypes()."""
    return bool(re.search(r'issuetype\s+not\s+in\s+subTaskIssueTypes\s*\(\s*\)', jql, re.IGNORECASE))


def _issue_in_sprint(issue: dict, sprint_id: int) -> bool:
    """True when the issue's `customfield_10020` lists the given sprint id."""
    sprints = issue.get("fields", {}).get("customfield_10020") or []
    return any(isinstance(s, dict) and s.get("id") == sprint_id for s in sprints)


def _issue_is_subtask(issue: dict) -> bool:
    """True when the issue's `fields.issuetype.subtask` flag is set. Falls
    back to a name match (Sub-Task / Sub-task / Teilaufgabe) so the mock
    stays usable for clients that omit the `subtask` flag."""
    it = issue.get("fields", {}).get("issuetype") or {}
    if isinstance(it, dict) and it.get("subtask") is True:
        return True
    name = (it.get("name") if isinstance(it, dict) else "") or ""
    return name.lower() in ("sub-task", "subtask", "sub task", "teilaufgabe")


def _parse_cql_keyword(cql: str) -> str:
    """Return the keyword from  text ~ "keyword"  in a CQL string."""
    m = re.search(r'text\s*~\s*["\']([^"\']+)["\']', cql, re.IGNORECASE)
    return m.group(1).strip() if m else ""


def _parse_cql_spaces(cql: str) -> list:
    """Return list of space keys from space = "X" or space IN ("X","Y")."""
    keys = []
    for m in re.finditer(r'space\s*=\s*["\']([^"\']+)["\']', cql, re.IGNORECASE):
        keys.append(m.group(1).upper())
    m = re.search(r'space\s+IN\s*\(([^)]+)\)', cql, re.IGNORECASE)
    if m:
        for k in re.findall(r'["\']([^"\']+)["\']', m.group(1)):
            keys.append(k.upper())
    return list(dict.fromkeys(keys))


def _search_jira(jql: str, max_results: int = 50, start_at: int = 0) -> dict:
    keyword = _parse_jql_keyword(jql).lower()
    projects = _parse_jql_projects(jql)
    sprint_id = _parse_jql_sprint_id(jql)
    exclude_subtasks = _parse_jql_exclude_subtasks(jql)

    results = []
    for issue in JIRA_ISSUES:
        # Project filter
        if projects and issue["project"] not in projects:
            continue
        # Sprint filter (numeric). Mirrors the real Jira API: `sprint = N`
        # matches every issue whose customfield_10020 lists sprint N.
        if sprint_id is not None and not _issue_in_sprint(issue, sprint_id):
            continue
        # Sub-task exclusion: `issuetype not in subTaskIssueTypes()`.
        if exclude_subtasks and _issue_is_subtask(issue):
            continue
        # Keyword filter (substring match on summary, description and comments)
        if keyword:
            summary = (issue["fields"].get("summary") or "").lower()
            desc = (issue["fields"].get("description") or "").lower()
            comments_text = " ".join(
                (c.get("body") or "") for c in
                issue["fields"].get("comment", {}).get("comments", [])
            ).lower()
            if keyword not in summary and keyword not in desc and keyword not in comments_text:
                continue
        results.append(issue)

    total = len(results)
    paged = results[start_at: start_at + max_results]

    return {
        "issues": paged,
        "total": total,
        "startAt": start_at,
        "maxResults": max_results,
    }


def _search_confluence(cql: str, limit: int = 25, start: int = 0) -> dict:
    keyword = _parse_cql_keyword(cql).lower()
    spaces = _parse_cql_spaces(cql)

    results = []
    for page in CONFLUENCE_PAGES:
        # Space filter
        if spaces and page["space"]["key"].upper() not in spaces:
            continue
        # Keyword filter (substring match on title + body.view.value)
        if keyword:
            title = (page.get("title") or "").lower()
            body_html = (page.get("body", {}).get("view", {}).get("value") or "").lower()
            # strip HTML tags for matching
            body_text = re.sub(r'<[^>]+>', ' ', body_html)
            if keyword not in title and keyword not in body_text:
                continue
        results.append(page)

    total = len(results)
    paged = results[start: start + limit]

    return {
        "results": paged,
        "start": start,
        "limit": limit,
        "size": total,
    }


def _empty_attachment_response() -> dict:
    return {"results": [], "start": 0, "limit": 25, "size": 0}


# ── Jira endpoints ────────────────────────────────────────────────────────────

@app.route("/rest/api/2/search", methods=["GET"])
def jira_v2_get():
    jql = request.args.get("jql", "")
    max_results = min(int(request.args.get("maxResults", 50)), 100)
    start_at = int(request.args.get("startAt", 0))
    return jsonify(_search_jira(jql, max_results, start_at))


@app.route("/rest/api/2/search", methods=["POST"])
def jira_v2_post():
    body = request.get_json(silent=True) or {}
    jql = body.get("jql", "")
    max_results = min(int(body.get("maxResults", 50)), 100)
    start_at = int(body.get("startAt", 0))
    return jsonify(_search_jira(jql, max_results, start_at))


@app.route("/rest/api/3/search", methods=["GET"])
def jira_v3_get():
    jql = request.args.get("jql", "")
    max_results = min(int(request.args.get("maxResults", 50)), 100)
    start_at = int(request.args.get("startAt", 0))
    return jsonify(_search_jira(jql, max_results, start_at))


@app.route("/rest/api/3/search", methods=["POST"])
def jira_v3_post():
    body = request.get_json(silent=True) or {}
    jql = body.get("jql", "")
    max_results = min(int(body.get("maxResults", 50)), 100)
    start_at = int(body.get("startAt", 0))
    return jsonify(_search_jira(jql, max_results, start_at))


@app.route("/rest/api/3/search/jql", methods=["POST"])
def jira_v3_jql():
    body = request.get_json(silent=True) or {}
    jql = body.get("jql", "")
    max_results = min(int(body.get("maxResults", 50)), 100)
    start_at = int(body.get("startAt", 0))
    return jsonify(_search_jira(jql, max_results, start_at))


@app.route("/rest/api/2/serverInfo", methods=["GET"])
@app.route("/rest/api/3/serverInfo", methods=["GET"])
def jira_server_info():
    return jsonify({
        "baseUrl": request.host_url.rstrip("/"),
        "version": "9.12.0",
        "buildNumber": 90120,
        "serverTitle": "Demo Jira (StackLens Mock)",
        "serverTime": "2025-01-01T00:00:00.000+0000",
        "deploymentType": "Server",
    })


# ── Jira single-issue endpoint (used by Ticket-Analyse) ──────────────────────

@app.route("/rest/api/2/issue/<issue_key>", methods=["GET"])
@app.route("/rest/api/3/issue/<issue_key>", methods=["GET"])
def jira_get_issue(issue_key):
    key = issue_key.upper()
    for issue in JIRA_ISSUES:
        if issue["key"].upper() == key:
            return jsonify(issue)
    return jsonify({"errorMessages": [f"Issue {issue_key} not found"], "errors": {}}), 404


# ── Jira issue update endpoint (PUT) ─────────────────────────────────────────────
# Used by the StackLens Sprint-Review feature to APPEND an ADF V3 table to
# the description of the user-supplied target ticket. The demo merges the
# incoming description with the existing one so successive runs accumulate
# tables (mirroring the real Jira behaviour).

@app.route("/rest/api/2/issue/<issue_key>", methods=["PUT"])
@app.route("/rest/api/3/issue/<issue_key>", methods=["PUT"])
def jira_put_issue(issue_key):
    """Update an issue's description.

    The fake persists the merge into the in-memory [JIRA_ISSUES] list so the
    developer can re-fetch the issue via `GET /rest/api/{2,3}/issue/<key>`
    and see the appended table.
    """
    key = issue_key.upper()
    payload = request.get_json(silent=True) or {}
    fields = payload.get("fields") or {}
    incoming_description = fields.get("description")
    if incoming_description is None:
        return jsonify({"errorMessages": ["No description supplied"], "errors": {}}), 400
    for issue in JIRA_ISSUES:
        if issue["key"].upper() == key:
            existing = issue["fields"].get("description")
            # Cloud v3 sends ADF JSON (object); Server/DC v2 sends plain text.
            # We persist in the same shape the caller sent so successive
            # GETs return what the previous PUT wrote.
            issue["fields"]["description"] = incoming_description
            print(
                f"[fake-atlassian] PUT issue={key} desc_type="
                f"{type(incoming_description).__name__} "
                f"old={'(empty)' if not existing else 'present'}"
            )
            return jsonify({"id": issue["id"], "key": issue["key"], "self": request.url})
    return jsonify({"errorMessages": [f"Issue {issue_key} not found"], "errors": {}}), 404


# ── Jira comment endpoint (used by Sprint-Vorbereitung) ──────────────────────

@app.route("/rest/api/2/issue/<issue_key>/comment", methods=["POST"])
@app.route("/rest/api/3/issue/<issue_key>/comment", methods=["POST"])
def jira_post_comment(issue_key):
    key = issue_key.upper()
    body = request.get_json(silent=True) or {}
    # Atlassian Cloud (v3) sends an ADF body; Server/DC (v2) sends a plain string
    text = body.get("body")
    if isinstance(text, dict):
        try:
            blocks = text.get("content", [])
            parts = []
            for blk in blocks:
                for inner in blk.get("content", []):
                    if inner.get("type") == "text":
                        parts.append(inner.get("text", ""))
                parts.append("\n")
            text = "".join(parts).strip()
        except Exception:
            text = str(text)
    text = text or ""
    for issue in JIRA_ISSUES:
        if issue["key"].upper() == key:
            comments = issue["fields"].setdefault("comment", {"comments": []})
            comments_list = comments.setdefault("comments", [])
            new_id = str(10000 + len(comments_list) + 1)
            new_comment = {
                "id": new_id,
                "author": {"displayName": "StackLens Sprint-Prep"},
                "body": text,
                "created": "2026-01-01T00:00:00.000+0000",
            }
            comments_list.append(new_comment)
            return jsonify(new_comment), 201
    return jsonify({"errorMessages": [f"Issue {issue_key} not found"], "errors": {}}), 404


# ── Confluence Server endpoints ───────────────────────────────────────────────

@app.route("/rest/api/user/current", methods=["GET"])
def confluence_server_user_current():
    return jsonify({
        "type": "known",
        "username": "demo-user",
        "displayName": "Demo User",
        "accountId": "demo-account-id",
        "isExternalCollaborator": False,
    })


@app.route("/rest/api/content/search", methods=["GET"])
def confluence_server_search():
    cql = request.args.get("cql", "")
    limit = min(int(request.args.get("limit", 25)), 100)
    start = int(request.args.get("start", 0))
    return jsonify(_search_confluence(cql, limit, start))


@app.route("/rest/api/content/<page_id>/child/attachment", methods=["GET"])
def confluence_server_attachment(page_id):
    return jsonify(_empty_attachment_response())


def _find_page(page_id: str):
    """Look up a Confluence page by its numeric/string id."""
    for page in CONFLUENCE_PAGES:
        if str(page.get("id")) == str(page_id):
            return page
    return None


@app.route("/rest/api/content/<page_id>", methods=["GET"])
def confluence_server_get_page(page_id):
    """Return the body of a Confluence page in the same shape as the real
    Confluence Server REST API v1 (`/rest/api/content/{id}`). The DB-Import
    Confluence hierarchy walker hits this endpoint to retrieve the HTML
    body of each page in the hierarchy it walks."""
    page = _find_page(page_id)
    if page is None:
        return jsonify({"message": f"page {page_id} not found"}), 404
    return jsonify({
        "id": page["id"],
        "type": page.get("type", "page"),
        "title": page.get("title", ""),
        "space": page.get("space", {}),
        "version": {"number": 1},
        "body": page.get("body", {}),
    })


@app.route("/rest/api/content/<page_id>/child/page", methods=["GET"])
def confluence_server_child_pages(page_id):
    """Return the child pages of a Confluence page. The hierarchy walker
    uses this endpoint to recurse into subpages when
    `includeChildren=true` is requested."""
    limit = min(int(request.args.get("limit", 25)), 100)
    start = int(request.args.get("start", 0))
    children = [
        {"id": p["id"]}
        for p in CONFLUENCE_PAGES
        if str(p.get("parentId")) == str(page_id)
    ]
    total = len(children)
    paged = children[start: start + limit]
    return jsonify({
        "results": paged,
        "start": start,
        "limit": limit,
        "size": total,
    })


# ── Confluence Cloud endpoints ────────────────────────────────────────────────

@app.route("/wiki/rest/api/user/current", methods=["GET"])
def confluence_cloud_user_current():
    return jsonify({
        "type": "known",
        "username": "demo-user",
        "displayName": "Demo User",
        "accountId": "demo-account-id",
        "isExternalCollaborator": False,
    })


@app.route("/wiki/rest/api/content/search", methods=["GET"])
def confluence_cloud_search():
    cql = request.args.get("cql", "")
    limit = min(int(request.args.get("limit", 25)), 100)
    start = int(request.args.get("start", 0))
    return jsonify(_search_confluence(cql, limit, start))


@app.route("/wiki/rest/api/content/<page_id>", methods=["GET"])
def confluence_cloud_get_page(page_id):
    """Same shape as the Server endpoint, exposed under the Cloud prefix
    (`/wiki/rest/api/content/{id}`). Reused by both stacklens config
    modes."""
    page = _find_page(page_id)
    if page is None:
        return jsonify({"message": f"page {page_id} not found"}), 404
    return jsonify({
        "id": page["id"],
        "type": page.get("type", "page"),
        "title": page.get("title", ""),
        "space": page.get("space", {}),
        "version": {"number": 1},
        "body": page.get("body", {}),
    })


@app.route("/wiki/rest/api/content/<page_id>/child/page", methods=["GET"])
def confluence_cloud_child_pages(page_id):
    """Same shape as the Server endpoint, exposed under the Cloud prefix."""
    limit = min(int(request.args.get("limit", 25)), 100)
    start = int(request.args.get("start", 0))
    children = [
        {"id": p["id"]}
        for p in CONFLUENCE_PAGES
        if str(p.get("parentId")) == str(page_id)
    ]
    total = len(children)
    paged = children[start: start + limit]
    return jsonify({
        "results": paged,
        "start": start,
        "limit": limit,
        "size": total,
    })


@app.route("/wiki/rest/api/content/<page_id>/child/attachment", methods=["GET"])
def confluence_cloud_attachment(page_id):
    return jsonify(_empty_attachment_response())


# ── Jira Agile API (Sprint Board) ─────────────────────────────────────────────
# Used by the Review-Analyse feature in StackLens.

@app.route("/rest/agile/1.0/board", methods=["GET"])
def agile_boards():
    """Return all Jira Agile boards visible to the demo user.

    Used by the StackLens Sprint-Review feature to discover which boards
    contain sprints so the sprint-name lookup can scan every board.
    """
    boards = [
        {"id": 5, "name": "Demo Board", "type": "scrum", "location": {"projectKey": "DEMO"}},
    ]
    max_results = min(int(request.args.get("maxResults", 50)), 100)
    start_at = int(request.args.get("startAt", 0))
    paged = boards[start_at: start_at + max_results]
    return jsonify({
        "maxResults": max_results,
        "startAt": start_at,
        "isLast": (start_at + len(paged)) >= len(boards),
        "values": paged,
    })


@app.route("/rest/agile/1.0/sprint/<int:sprint_id>", methods=["GET"])
def agile_sprint_get(sprint_id):
    """Return sprint metadata for the given sprint ID."""
    for sprints in SPRINT_BOARDS.values():
        for sprint in sprints:
            if sprint["id"] == sprint_id:
                return jsonify(sprint)
    return jsonify({"errorMessages": [f"Sprint {sprint_id} not found"], "errors": {}}), 404


@app.route("/rest/agile/1.0/sprint/<int:sprint_id>/issue", methods=["GET"])
def agile_sprint_issues(sprint_id):
    """Return paginated issues for the given sprint ID."""
    issues = SPRINT_ISSUES.get(sprint_id)
    if issues is None:
        return jsonify({"errorMessages": [f"Sprint {sprint_id} not found"], "errors": {}}), 404

    max_results = min(int(request.args.get("maxResults", 50)), 100)
    start_at = int(request.args.get("startAt", 0))
    total = len(issues)
    paged = issues[start_at: start_at + max_results]

    return jsonify({
        "expand": "names,schema",
        "startAt": start_at,
        "maxResults": max_results,
        "total": total,
        "issues": paged,
    })


@app.route("/rest/agile/1.0/board/<int:board_id>/sprint", methods=["GET"])
def agile_board_sprints(board_id):
    """Return sprints for the given board (supports ?state=active filter)."""
    sprints = SPRINT_BOARDS.get(board_id, [])
    state_filter = request.args.get("state", "").lower()
    if state_filter:
        sprints = [s for s in sprints if s.get("state", "").lower() == state_filter]

    max_results = min(int(request.args.get("maxResults", 50)), 100)
    start_at = int(request.args.get("startAt", 0))
    paged = sprints[start_at: start_at + max_results]

    return jsonify({
        "maxResults": max_results,
        "startAt": start_at,
        "isLast": (start_at + len(paged)) >= len(sprints),
        "values": paged,
    })


# ── Health / info ─────────────────────────────────────────────────────────────

@app.route("/", methods=["GET"])
@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "UP",
        "service": "fake-atlassian",
        "description": "StackLens Demo: Jira + Confluence mock API",
        "endpoints": {
            "jira_v2": "/rest/api/2/search",
            "jira_v3": "/rest/api/3/search",
            "jira_v3_jql": "/rest/api/3/search/jql",
            "jira_agile_sprint": "/rest/agile/1.0/sprint/{id}",
            "jira_agile_sprint_issues": "/rest/agile/1.0/sprint/{id}/issue",
            "jira_agile_board_sprints": "/rest/agile/1.0/board/{id}/sprint",
            "confluence_server_user": "/rest/api/user/current",
            "confluence_server": "/rest/api/content/search",
            "confluence_cloud_user": "/wiki/rest/api/user/current",
            "confluence_cloud": "/wiki/rest/api/content/search",
        },
        "data": {
            "jira_issues": len(JIRA_ISSUES),
            "confluence_pages": len(CONFLUENCE_PAGES),
            "sprint_boards": len(SPRINT_BOARDS),
            "sprint_issues": sum(len(v) for v in SPRINT_ISSUES.values()),
        },
        "demo_review_url": "http://localhost:8081/jira/software/projects/DEMO/boards/5/sprints/42",
    })


if __name__ == "__main__":
    print(f"[fake-atlassian] Starting mock Atlassian API on port 8081")
    print(f"[fake-atlassian] Loaded {len(JIRA_ISSUES)} Jira issues, {len(CONFLUENCE_PAGES)} Confluence pages")
    # threaded=True: Flask's built-in dev server is single-threaded by default,
    # which makes StackLens' parallel health probes (one request per active
    # Jira/Confluence config) race against each other and surface as
    # `reactor.netty.http.client.PrematureCloseException` on the client side.
    app.run(host="0.0.0.0", port=8081, debug=False, threaded=True)
