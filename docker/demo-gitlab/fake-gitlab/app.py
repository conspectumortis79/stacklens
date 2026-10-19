"""
Fake GitLab API Server
Simulates GitLab REST API v4 for StackLens testing.

Endpoints:
  GET  /api/v4/user                                       – current authenticated user
  GET  /api/v4/projects                                   – list all accessible projects
  GET  /api/v4/projects/<id>                              – get single project
  GET  /api/v4/groups/<group>/projects                    – projects belonging to a group
  GET  /api/v4/projects/<id>/repository/tree              – recursive file tree
  GET  /api/v4/projects/<id>/repository/files/<path>/raw  – raw file content
  GET  /api/v4/projects/<id>/search?scope=blobs           – keyword search (blob)
  GET  /api/v4/search?scope=blobs                         – global keyword search
  GET  /api/v4/projects/<id>/merge_requests               – list MRs (supports ?search=)
  GET  /api/v4/projects/<id>/merge_requests/<iid>/changes – MR diff changes
  POST /api/v4/projects/<id>/repository/branches          – create branch (demo: in-memory)
  POST /api/v4/projects/<id>/repository/commits           – commit files  (demo: in-memory)
  POST /api/v4/projects/<id>/merge_requests               – create MR     (demo: in-memory)

Authentication: any PRIVATE-TOKEN header accepted – no validation.
"""

import hashlib
import os
import re
import subprocess
from urllib.parse import unquote

from flask import Flask, Response, jsonify, request
from flask_cors import CORS

from data import GROUPS, MERGE_REQUESTS, PROJECT_FILES, PROJECTS

# Where the bare repos for `git clone` live. Path mirrors the GitLab
# URL layout so `<host>/<group>/<project>.git` is served by
# `git http-backend` from `/app/repos/<group>/<project>.git`.
GIT_PROJECT_ROOT = os.environ.get("GIT_PROJECT_ROOT", "/app/repos")

app = Flask(__name__)
CORS(app)

# ── In-memory store for demo-created branches / commits / MRs ────────────────
# Survives only for the lifetime of the running container process.
_demo_branches: dict[int, list[dict]] = {}          # project_id -> [branch, ...]
_demo_commits:  dict[int, list[dict]] = {}          # project_id -> [commit, ...]
_demo_branch_changes: dict[tuple, list[dict]] = {}  # (project_id, branch) -> [change, ...]
_demo_mrs:      dict[int, list[dict]] = {}          # project_id -> [mr, ...]
_demo_mr_next_iid: dict[int, int]     = {}   # project_id -> next iid counter
_demo_mr_next_id  = 1000                     # global id counter
_demo_discussions: dict[str, list[dict]] = {}  # key: "{project_id}:{iid}" -> [discussion, ...]

# ── Helpers ───────────────────────────────────────────────────────────────────


def _all_projects() -> list[dict]:
    return PROJECTS


def _project_by_id(project_id: int) -> dict | None:
    return next((p for p in PROJECTS if p["id"] == project_id), None)


def _resolve_project(project_ref: str) -> dict | None:
    """Resolve a project by numeric ID string or namespace path (e.g. 'group/repo')."""
    if project_ref.isdigit():
        return _project_by_id(int(project_ref))
    path_lower = project_ref.lower()
    return next((p for p in PROJECTS if p["path_with_namespace"].lower() == path_lower), None)


def _fake_sha(text: str) -> str:
    return hashlib.md5(text.encode()).hexdigest()[:16]  # noqa: S324 (non-crypto usage)


def _build_tree(project_id: int) -> list[dict]:
    """Build a GitLab tree response from the flat {path: content} dict."""
    files = PROJECT_FILES.get(project_id, {})
    entries = []
    for path in files:
        name = path.rsplit("/", 1)[-1]
        entries.append(
            {
                "id": _fake_sha(path),
                "name": name,
                "path": path,
                "type": "blob",
            }
        )
    return entries


def _get_file_content(project_id: int, file_path: str) -> str | None:
    files = PROJECT_FILES.get(project_id, {})
    return files.get(file_path)


def _search_blobs(project_id: int, keyword: str) -> list[dict]:
    """
    Simulate GitLab blob search: scan all files for the keyword,
    return matching code context in GitLab blob result format.
    """
    files = PROJECT_FILES.get(project_id, {})
    results = []
    keyword_lower = keyword.lower()

    for file_path, content in files.items():
        lines = content.splitlines()
        for i, line in enumerate(lines):
            if keyword_lower in line.lower():
                # Build context: 2 lines before + match line + 2 lines after
                start = max(0, i - 2)
                end = min(len(lines), i + 3)
                context = "\n".join(lines[start:end])
                results.append(
                    {
                        "project_id": project_id,
                        "path": file_path,
                        "ref": "main",
                        "startline": i + 1,
                        "data": context,
                    }
                )
                break  # one match per file is enough for demo purposes

    return results


def _paginate(items: list, page: int, per_page: int) -> list:
    start = (page - 1) * per_page
    return items[start: start + per_page]


def _esc(text: str) -> str:
    """HTML-escape a string for safe inline output."""
    return (
        str(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def _mr_changes_for(project_id: int, mr: dict) -> list[dict]:
    """Return the file-change list for a demo MR, falling back to branch-level commits."""
    stored = mr.get("changes") or []
    if stored:
        return stored
    branch = mr.get("source_branch", "")
    return _demo_branch_changes.get((project_id, branch), [])


def _render_diff_lines(diff_str: str, file_id: str = "") -> str:
    """Convert a unified diff string to syntax-highlighted HTML spans with line anchors."""
    parts = []
    new_line = 0  # current line number on the new (right-side) file
    for line in (diff_str or "").splitlines():
        if line.startswith("@@"):
            cls = "dh"
            m = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)", line)
            if m:
                new_line = int(m.group(1))
            parts.append(f'<span class="{cls}">{_esc(line)}\n</span>')
        elif line.startswith("+"):
            anchor = f' id="L{file_id}_{new_line}"' if file_id else ""
            parts.append(f'<span class="da"{anchor}>{_esc(line)}\n</span>')
            new_line += 1
        elif line.startswith("-"):
            parts.append(f'<span class="dd">{_esc(line)}\n</span>')
        else:
            anchor = f' id="L{file_id}_{new_line}"' if file_id else ""
            parts.append(f'<span class="dc"{anchor}>{_esc(line)}\n</span>')
            new_line += 1
    return "".join(parts)


# ── Auth / user endpoint ──────────────────────────────────────────────────────


@app.route("/api/v4/user", methods=["GET"])
def current_user():
    """Return a fake authenticated user so connection tests pass."""
    return jsonify({
        "id": 1,
        "username": "demo-user",
        "name": "Demo User",
        "email": "demo@stacklens.local",
        "state": "active",
        "is_admin": False,
    })


# ── Project endpoints ─────────────────────────────────────────────────────────


@app.route("/api/v4/projects", methods=["GET"])
def list_projects():
    page = int(request.args.get("page", 1))
    per_page = min(int(request.args.get("per_page", 20)), 100)
    projects = _paginate(_all_projects(), page, per_page)
    return jsonify(projects)


@app.route("/api/v4/projects/<int:project_id>", methods=["GET"])
def get_project(project_id: int):
    project = _project_by_id(project_id)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404
    return jsonify(project)


@app.route("/api/v4/groups/<group_id>/projects", methods=["GET"])
def group_projects(group_id: str):
    group_path = unquote(group_id).lower()
    page = int(request.args.get("page", 1))
    per_page = min(int(request.args.get("per_page", 20)), 100)

    # Support both numeric group ID and path (e.g. "10" or "demo-group")
    try:
        numeric_id = int(group_path)
    except ValueError:
        numeric_id = None

    matched = [
        p for p in PROJECTS
        if p["path_with_namespace"].lower().startswith(group_path + "/")
        or p.get("namespace", {}).get("path", "").lower() == group_path
        or (numeric_id is not None and p.get("namespace", {}).get("id") == numeric_id)
    ]
    return jsonify(_paginate(matched, page, per_page))


# ── Repository tree ───────────────────────────────────────────────────────────


@app.route("/api/v4/projects/<int:project_id>/repository/tree", methods=["GET"])
def repo_tree(project_id: int):
    if not _project_by_id(project_id):
        return jsonify({"message": "404 Project Not Found"}), 404

    page = int(request.args.get("page", 1))
    per_page = min(int(request.args.get("per_page", 100)), 100)
    entries = _paginate(_build_tree(project_id), page, per_page)
    return jsonify(entries)


# ── Raw file content ──────────────────────────────────────────────────────────


@app.route(
    "/api/v4/projects/<int:project_id>/repository/files/<path:encoded_path>/raw",
    methods=["GET"],
)
def file_raw(project_id: int, encoded_path: str):
    # encoded_path may be URL-encoded (e.g. src%2Fmain%2F...):
    file_path = unquote(encoded_path)

    if not _project_by_id(project_id):
        return jsonify({"message": "404 Project Not Found"}), 404

    content = _get_file_content(project_id, file_path)
    if content is None:
        return jsonify({"message": "404 File Not Found"}), 404

    return app.response_class(content, mimetype="text/plain; charset=utf-8")


# ── Blob search ───────────────────────────────────────────────────────────────


@app.route("/api/v4/projects/<int:project_id>/search", methods=["GET"])
def project_search(project_id: int):
    scope = request.args.get("scope", "")
    keyword = request.args.get("search", "")
    page = int(request.args.get("page", 1))
    per_page = min(int(request.args.get("per_page", 20)), 100)

    if scope != "blobs":
        return jsonify([])

    if not _project_by_id(project_id):
        return jsonify({"message": "404 Project Not Found"}), 404

    if not keyword:
        return jsonify([])

    results = _search_blobs(project_id, keyword)
    return jsonify(_paginate(results, page, per_page))


@app.route("/api/v4/search", methods=["GET"])
def global_search():
    """Global blob search across all projects."""
    scope = request.args.get("scope", "")
    keyword = request.args.get("search", "")
    page = int(request.args.get("page", 1))
    per_page = min(int(request.args.get("per_page", 20)), 100)

    if scope != "blobs" or not keyword:
        return jsonify([])

    results = []
    for project in PROJECTS:
        results.extend(_search_blobs(project["id"], keyword))

    return jsonify(_paginate(results, page, per_page))


# ── Merge requests ────────────────────────────────────────────────────────────


@app.route("/api/v4/projects/<int:project_id>/merge_requests", methods=["GET"])
def merge_requests(project_id: int):
    search = request.args.get("search", "").lower()
    state = request.args.get("state", "")
    page = int(request.args.get("page", 1))
    per_page = min(int(request.args.get("per_page", 20)), 100)

    if not _project_by_id(project_id):
        return jsonify([])

    # Merge seed data with any demo-created MRs
    mrs = list(MERGE_REQUESTS.get(project_id, [])) + _demo_mrs.get(project_id, [])

    if state:
        mrs = [mr for mr in mrs if mr.get("state") == state]

    if search:
        mrs = [
            mr for mr in mrs
            if search in mr.get("title", "").lower()
            or search in mr.get("description", "").lower()
            or search in mr.get("source_branch", "").lower()
        ]

    return jsonify(_paginate(mrs, page, per_page))


@app.route(
    "/api/v4/projects/<int:project_id>/merge_requests/<int:iid>/changes",
    methods=["GET"],
)
def mr_changes(project_id: int, iid: int):
    # Look in both seed data and demo-created MRs
    mrs = list(MERGE_REQUESTS.get(project_id, [])) + _demo_mrs.get(project_id, [])
    mr = next((m for m in mrs if m["iid"] == iid), None)
    if not mr:
        return jsonify({"message": "404 Not found"}), 404

    return jsonify(
        {
            "id": mr["id"],
            "iid": mr["iid"],
            "title": mr["title"],
            "state": mr["state"],
            "changes": _mr_changes_for(project_id, mr),
        }
    )


# ── Path-based MR endpoints (project referenced by namespace/name) ─────────────
# The StackLens adapter URL-encodes the project path (e.g. demo-group%2Fecommerce-api)
# which Flask decodes back to a slash-separated path before routing.


@app.route(
    "/api/v4/projects/<path:project_ref>/merge_requests/<int:iid>",
    methods=["GET"],
)
def mr_single_by_path(project_ref: str, iid: int):
    project = _resolve_project(project_ref)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404
    project_id = project["id"]
    mrs = list(MERGE_REQUESTS.get(project_id, [])) + _demo_mrs.get(project_id, [])
    mr = next((m for m in mrs if m["iid"] == iid), None)
    if not mr:
        return jsonify({"message": "404 Not found"}), 404
    return jsonify(mr)


@app.route(
    "/api/v4/projects/<path:project_ref>/merge_requests/<int:iid>/changes",
    methods=["GET"],
)
def mr_changes_by_path(project_ref: str, iid: int):
    project = _resolve_project(project_ref)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404
    project_id = project["id"]
    mrs = list(MERGE_REQUESTS.get(project_id, [])) + _demo_mrs.get(project_id, [])
    mr = next((m for m in mrs if m["iid"] == iid), None)
    if not mr:
        return jsonify({"message": "404 Not found"}), 404
    return jsonify(
        {
            "id": mr["id"],
            "iid": mr["iid"],
            "title": mr["title"],
            "state": mr["state"],
            "changes": _mr_changes_for(project_id, mr),
        }
    )


# ── MR discussion / inline suggestion (demo write endpoint) ───────────────────


@app.route(
    "/api/v4/projects/<path:project_ref>/merge_requests/<int:iid>/discussions",
    methods=["POST"],
)
def create_mr_discussion(project_ref: str, iid: int):
    """Accept an inline code-suggestion posted by StackLens, store it, and return a note URL."""
    project = _resolve_project(project_ref)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404
    disc_key = f"{project['id']}:{iid}"
    note_id = 9000 + len(_demo_discussions.get(disc_key, []))
    web_url = f"http://localhost:3080/{project_ref}/-/merge_requests/{iid}#note_{note_id}"
    body = (request.get_json(silent=True) or {}).get("body", "")
    note = {"id": note_id, "body": body, "web_url": web_url}
    disc = {"id": f"demo-discussion-{iid}-{note_id}", "notes": [note]}
    _demo_discussions.setdefault(disc_key, []).append(disc)
    return jsonify(disc), 201


# ── Branch creation (demo write endpoint) ─────────────────────────────────────


@app.route("/api/v4/projects/<int:project_id>/repository/branches", methods=["POST"])
def create_branch(project_id: int):
    project = _project_by_id(project_id)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404

    body = request.get_json(silent=True) or {}
    branch_name = body.get("branch", "")
    ref = body.get("ref", project.get("default_branch", "main"))

    if not branch_name:
        return jsonify({"message": "Branch name is required"}), 400

    # Idempotent: return success even if branch "already exists" in demo
    existing = next(
        (b for b in _demo_branches.get(project_id, []) if b["name"] == branch_name), None
    )
    if existing:
        return jsonify(existing)

    branch = {
        "name": branch_name,
        "merged": False,
        "protected": False,
        "default": False,
        "web_url": f"{project['web_url']}/-/tree/{branch_name}",
        "commit": {"id": _fake_sha(ref), "short_id": _fake_sha(ref)[:8], "message": f"Branch off {ref}"},
    }
    _demo_branches.setdefault(project_id, []).append(branch)
    return jsonify(branch), 201


# ── Commit creation (demo write endpoint) ─────────────────────────────────────


@app.route("/api/v4/projects/<int:project_id>/repository/commits", methods=["POST"])
def create_commit(project_id: int):
    project = _project_by_id(project_id)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404

    body = request.get_json(silent=True) or {}
    commit_message = body.get("commit_message", "Auto-generated commit")
    branch = body.get("branch", project.get("default_branch", "main"))
    actions = body.get("actions", [])

    commit_sha = _fake_sha(f"{project_id}:{branch}:{commit_message}:{len(actions)}")
    commit = {
        "id": commit_sha,
        "short_id": commit_sha[:8],
        "title": commit_message,
        "message": commit_message,
        "author_name": "StackLens Demo",
        "authored_date": "2026-03-25T12:00:00.000Z",
        "committed_date": "2026-03-25T12:00:00.000Z",
        "web_url": f"{project['web_url']}/-/commit/{commit_sha}",
        "stats": {"additions": len(actions) * 20, "deletions": 0, "total": len(actions) * 20},
    }
    _demo_commits.setdefault(project_id, []).append(commit)

    # ── Store file changes keyed by (project_id, branch) ─────────────────────
    # The MR may not exist yet (commit happens before openMr in StackLens),
    # so we store changes here and serve them lazily from mr_changes endpoints.
    key = (project_id, branch)
    for action in actions:
        file_path   = action.get("file_path", "")
        old_path    = action.get("previous_path") or file_path
        action_type = action.get("action", "update")
        new_file    = action_type == "create"
        deleted     = action_type == "delete"
        content     = action.get("content", "")
        diff_lines  = [] if deleted else [f"+{line}" for line in content.splitlines()]
        _demo_branch_changes.setdefault(key, []).append({
            "old_path": old_path,
            "new_path": file_path,
            "new_file": new_file,
            "deleted_file": deleted,
            "diff": "\n".join(diff_lines),
        })

    return jsonify(commit), 201


# ── MR creation (demo write endpoint) ─────────────────────────────────────────


@app.route("/api/v4/projects/<int:project_id>/merge_requests", methods=["POST"])
def create_merge_request(project_id: int):
    global _demo_mr_next_id  # noqa: PLW0603

    project = _project_by_id(project_id)
    if not project:
        return jsonify({"message": "404 Project Not Found"}), 404

    body = request.get_json(silent=True) or {}
    source_branch = body.get("source_branch", "")
    target_branch = body.get("target_branch", project.get("default_branch", "main"))
    title = body.get("title", "Auto-generated MR")
    description = body.get("description", "")

    if not source_branch:
        return jsonify({"message": "source_branch is required"}), 400

    # Determine next iid for this project
    existing_iids = (
        [mr["iid"] for mr in MERGE_REQUESTS.get(project_id, [])]
        + [mr["iid"] for mr in _demo_mrs.get(project_id, [])]
    )
    next_iid = (max(existing_iids) + 1) if existing_iids else 1

    mr_id = _demo_mr_next_id
    _demo_mr_next_id += 1

    mr = {
        "id": mr_id,
        "iid": next_iid,
        "title": title,
        "description": description,
        "state": "opened",
        "source_branch": source_branch,
        "target_branch": target_branch,
        "web_url": (
            f"{project['web_url']}/-/merge_requests/{next_iid}"
        ),
        "author": {"name": "StackLens"},
        "changes": [],
    }
    _demo_mrs.setdefault(project_id, []).append(mr)
    return jsonify(mr), 201


# ── Namespace / group info ────────────────────────────────────────────────────


@app.route("/api/v4/groups/<group_id>", methods=["GET"])
def get_group(group_id: str):
    decoded = unquote(group_id).lower()
    group = next(
        (g for g in GROUPS if g["path"].lower() == decoded or str(g["id"]) == decoded),
        None,
    )
    if not group:
        return jsonify({"message": "404 Group Not Found"}), 404
    return jsonify(group)


# ── Version / health ──────────────────────────────────────────────────────────


@app.route("/api/v4/version", methods=["GET"])
def version():
    return jsonify({"version": "17.0.0-mock", "revision": "fake-gitlab"})


@app.route("/", methods=["GET"])
@app.route("/health", methods=["GET"])
def health():
    return jsonify(
        {
            "status": "UP",
            "service": "fake-gitlab",
            "description": "StackLens Demo: GitLab API mock",
            "projects": len(PROJECTS),
            "endpoints": {
                "list_projects": "GET /api/v4/projects",
                "group_projects": "GET /api/v4/groups/{group}/projects",
                "project": "GET /api/v4/projects/{id}",
                "tree": "GET /api/v4/projects/{id}/repository/tree",
                "file_raw": "GET /api/v4/projects/{id}/repository/files/{path}/raw",
                "blob_search": "GET /api/v4/projects/{id}/search?scope=blobs&search={keyword}",
                "global_search": "GET /api/v4/search?scope=blobs&search={keyword}",
                "merge_requests": "GET /api/v4/projects/{id}/merge_requests",
                "mr_changes": "GET /api/v4/projects/{id}/merge_requests/{iid}/changes",
                "create_branch": "POST /api/v4/projects/{id}/repository/branches",
                "create_commit": "POST /api/v4/projects/{id}/repository/commits",
                "create_mr": "POST /api/v4/projects/{id}/merge_requests",
            },
        }
    )


# ── MR HTML viewer ─────────────────────────────────────────────────────────────
# Handles the browser URLs that StackLens links to when showing MR results.

_MR_PAGE_CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#f6f8fa;color:#24292e;font-size:14px}
.pg-hdr{background:#fff;border-bottom:2px solid #d0d7de;padding:1rem 2rem}
.pg-hdr h1{font-size:1.2rem;font-weight:600;margin-bottom:.35rem;word-break:break-word}
.meta{font-size:.8rem;color:#57606a;display:flex;flex-wrap:wrap;gap:.5rem;align-items:center;margin-top:.4rem}
.badge{display:inline-block;padding:3px 10px;border-radius:12px;font-size:.72rem;font-weight:700}
.b-open{background:#1f883d;color:#fff}.b-merged{background:#8250df;color:#fff}.b-closed{background:#cf222e;color:#fff}
.content{max-width:1200px;margin:1.25rem auto;padding:0 1rem}
.description{background:#fff;border:1px solid #d0d7de;border-radius:6px;padding:1rem 1.25rem;margin-bottom:1.25rem;
  white-space:pre-wrap;font-size:.875rem;line-height:1.6}
h2.section-title{font-size:.95rem;font-weight:600;margin-bottom:.75rem;color:#24292e}
.file-block{background:#fff;border:1px solid #d0d7de;border-radius:6px;margin-bottom:.75rem;overflow:hidden}
.file-hdr{background:#f6f8fa;border-bottom:1px solid #d0d7de;padding:7px 14px;display:flex;align-items:center;gap:8px;
  font-family:ui-monospace,SFMono-Regular,monospace;font-size:.8rem;cursor:pointer}
.file-hdr:hover{background:#eef1f5}
.fl{font-family:sans-serif;font-size:.68rem;padding:2px 6px;border-radius:3px;font-weight:700}
.fl-new{background:#dafbe1;color:#116329}.fl-del{background:#ffebe9;color:#82071e}.fl-mod{background:#ddf4ff;color:#0550ae}
pre.diff{overflow-x:auto;font-family:ui-monospace,SFMono-Regular,monospace;font-size:.77rem;line-height:1.45;max-height:600px}
.dh{display:block;background:#ddf4ff;color:#0550ae;padding:0 8px}
.da{display:block;background:#e6ffed;color:#24292e;padding:0 8px}
.dd{display:block;background:#ffebe9;color:#24292e;padding:0 8px}
.dc{display:block;background:#fff;color:#57606a;padding:0 8px}
.suggestions{margin-top:1.5rem}
.note-card{background:#fff;border:1px solid #d0d7de;border-radius:6px;margin-bottom:.6rem;overflow:hidden}
.note-hdr{background:#f6f8fa;border-bottom:1px solid #d0d7de;padding:7px 14px;font-size:.8rem;color:#57606a}
.note-id{color:#0550ae;font-family:monospace;font-size:.75rem}
pre.note-body{padding:.75rem 1rem;font-family:ui-monospace,SFMono-Regular,monospace;font-size:.8rem;
  line-height:1.5;white-space:pre-wrap;word-break:break-word}
.empty{color:#57606a;font-style:italic;padding:.5rem 0}
"""


@app.route("/<path:project_ref>/-/merge_requests/<int:iid>", methods=["GET"])
@app.route("/<path:project_ref>/-/merge_requests/<int:iid>/diffs", methods=["GET"])
def mr_viewer(project_ref: str, iid: int):
    """Render a simple HTML MR diff viewer so StackLens file-links open correctly."""
    project = _resolve_project(project_ref)
    if not project:
        return Response("<h1>404 – Project not found</h1>", status=404, mimetype="text/html")

    project_id = project["id"]
    mrs = list(MERGE_REQUESTS.get(project_id, [])) + _demo_mrs.get(project_id, [])
    mr = next((m for m in mrs if m["iid"] == iid), None)
    if not mr:
        return Response("<h1>404 – MR not found</h1>", status=404, mimetype="text/html")

    changes = _mr_changes_for(project_id, mr)
    disc_key = f"{project_id}:{iid}"
    discussions = _demo_discussions.get(disc_key, [])

    title   = mr.get("title", "")
    desc    = mr.get("description", "")
    state   = mr.get("state", "open")
    author  = (mr.get("author") or {}).get("name", "Unknown")
    src     = mr.get("source_branch", "")
    tgt     = mr.get("target_branch", "main")

    badge_cls = {"merged": "b-merged", "closed": "b-closed"}.get(state, "b-open")

    # ── file diff blocks ────────────────────────────────────────────────────
    files_html = ""
    for change in changes:
        path   = change.get("new_path") or change.get("old_path", "")
        diff   = change.get("diff", "")
        is_new = change.get("new_file", False)
        is_del = change.get("deleted", False)
        label  = "new file" if is_new else ("deleted" if is_del else "modified")
        lcls   = "fl-new"  if is_new else ("fl-del"  if is_del else "fl-mod")
        # Slug used for anchor IDs: non-alphanumeric chars → dash
        file_id = re.sub(r"[^a-zA-Z0-9]", "-", path)
        files_html += (
            f'<div class="file-block" id="file-{file_id}">'
            f'<div class="file-hdr"><code>{_esc(path)}</code>'
            f'<span class="fl {lcls}">{label}</span></div>'
            f'<pre class="diff">{_render_diff_lines(diff, file_id)}</pre>'
            f"</div>"
        )

    # ── posted suggestions (notes) ──────────────────────────────────────────
    notes_html = ""
    total_notes = 0
    for disc in discussions:
        for note in disc.get("notes", []):
            note_id = note.get("id", "")
            body    = note.get("body", "")
            notes_html += (
                f'<div class="note-card" id="note_{note_id}">'
                f'<div class="note-hdr">StackLens Bot '
                f'<span class="note-id">#{note_id}</span></div>'
                f'<pre class="note-body">{_esc(body)}</pre>'
                f"</div>"
            )
            total_notes += 1

    sugs_section = (
        f'<div class="suggestions">'
        f'<h2 class="section-title">Suggestions posted ({total_notes})</h2>'
        f"{notes_html}"
        f"</div>"
    ) if notes_html else ""

    desc_block = (
        f'<div class="description">{_esc(desc)}</div>'
    ) if desc else ""

    html = f"""<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>!{iid} {_esc(title)}</title>
<style>{_MR_PAGE_CSS}</style>
</head>
<body>
<div class="pg-hdr">
  <h1>!{iid} {_esc(title)}&nbsp;<span class="badge {badge_cls}">{_esc(state)}</span></h1>
  <div class="meta">
    <span>by <strong>{_esc(author)}</strong></span>
    &nbsp;·&nbsp;
    <code>{_esc(src)}</code>&nbsp;→&nbsp;<code>{_esc(tgt)}</code>
  </div>
</div>
<div class="content">
  {desc_block}
  <h2 class="section-title">Changes ({len(changes)} files)</h2>
  {files_html or '<p class="empty">No changes recorded.</p>'}
  {sugs_section}
</div>
<script>
  // Toggle diff visibility on file header click
  document.querySelectorAll('.file-hdr').forEach(function(hdr) {{
    hdr.addEventListener('click', function() {{
      var pre = hdr.nextElementSibling;
      if (pre) pre.style.display = pre.style.display === 'none' ? '' : 'none';
    }});
  }});
</script>
</body>
</html>"""

    return Response(html, status=200, mimetype="text/html")


# ── Git smart-HTTP protocol (so `git clone http://<host>/<path>.git` works) ──
#
# `git http-backend` is a stock git CGI handler that implements the smart-HTTP
# protocol (advertising refs + serving pack data). We invoke it as a
# subprocess with the CGI environment variables it expects, parse its
# dual output (CGI headers + body), and forward it to the HTTP client.
#
# Routing covers both layouts seen in the wild:
#   - root-anchored:   GET /info/refs  +  POST /git-upload-pack
#   - path-anchored:   GET /<ns>/<name>.git/info/refs  +  POST /<ns>/<name>.git/git-upload-pack
# The first variant is what the application falls back to when no path
# info is present; the second is what `git clone` actually issues.

def _git_http_backend():
    """Invoke `git http-backend` as CGI for the current request."""
    body = request.get_data() if request.method == "POST" else b""
    env = os.environ.copy()
    env.update({
        # Required by git http-backend
        "GIT_PROJECT_ROOT": GIT_PROJECT_ROOT,
        "GIT_HTTP_EXPORT_ALL": "1",
        "PATH_INFO": request.path,
        "REQUEST_METHOD": request.method,
        "QUERY_STRING": request.query_string.decode("utf-8") if request.query_string else "",
        "CONTENT_TYPE": request.content_type or "",
        "CONTENT_LENGTH": str(len(body)),
        # Disable any active terminal / pager behaviour when git spawns
        # helpers; safer inside the container.
        "GIT_TERMINAL_PROMPT": "0",
    })
    try:
        proc = subprocess.run(
            ["git", "http-backend"],
            input=body,
            env=env,
            capture_output=True,
            cwd=GIT_PROJECT_ROOT,
            timeout=30,
        )
    except subprocess.TimeoutExpired:
        return Response("git http-backend timed out", status=504)
    except FileNotFoundError:
        return Response("git executable not found in container", status=500)

    output = proc.stdout or b""
    # CGI header/body separator: \r\n\r\n (preferred) or \n\n
    if b"\r\n\r\n" in output:
        header_blob, body_out = output.split(b"\r\n\r\n", 1)
    elif b"\n\n" in output:
        header_blob, body_out = output.split(b"\n\n", 1)
    else:
        header_blob, body_out = b"Status: 200 OK", output

    status_code = 200
    extra_headers = {}
    for line in header_blob.split(b"\n"):
        try:
            line_str = line.decode("utf-8", errors="ignore").strip()
        except Exception:
            continue
        if not line or ":" not in line_str:
            continue
        key, _, value = line_str.partition(":")
        key = key.strip()
        value = value.strip()
        if key.lower() == "status":
            parts = value.split(" ", 1)
            if parts[0].isdigit():
                status_code = int(parts[0])
        else:
            extra_headers[key] = value
    return Response(body_out, status=status_code, headers=extra_headers)


# Top-level (no project path) — used by clients that pin GIT_PROJECT_ROOT
# directly. Most clones use the path-anchored variant below.
@app.route("/info/refs", methods=["GET"])
def git_info_refs_root():
    return _git_http_backend()


@app.route("/git-upload-pack", methods=["POST"])
def git_upload_pack_root():
    return _git_http_backend()


# Path-anchored: /<group>/<name>.git/info/refs and /git-upload-pack
# Matches GitLab's actual URL layout (`<group>/<project>.git`).
@app.route("/<path:project_path>/info/refs", methods=["GET"])
def git_info_refs_path(project_path):
    # Append .git if the client forgot it (git always sends it, but be lenient).
    if not project_path.endswith(".git"):
        request.environ["PATH_INFO"] = f"/{project_path}.git{request.path[len('/' + project_path):]}"
    return _git_http_backend()


@app.route("/<path:project_path>/git-upload-pack", methods=["POST"])
def git_upload_pack_path(project_path):
    if not project_path.endswith(".git"):
        request.environ["PATH_INFO"] = f"/{project_path}.git/git-upload-pack"
    return _git_http_backend()


def _setup_bare_repos():
    """Create a bare git repo for every project in PROJECTS, populated
    with the files defined in PROJECT_FILES. Runs once at container
    startup; subsequent restarts reuse the existing repos.

    The path layout mirrors the GitLab URL: a project with
    `path_with_namespace = "demo-group/ecommerce-api"` is exposed at
    `http://<host>/demo-group/ecommerce-api.git` and stored locally at
    `/app/repos/demo-group/ecommerce-api.git`.
    """
    os.makedirs(GIT_PROJECT_ROOT, exist_ok=True)
    # Identity for the commits we create below. Without this `git commit`
    # would fail inside the container.
    subprocess.run(
        ["git", "config", "--global", "user.email", "demo@stacklens.local"],
        check=False, capture_output=True,
    )
    subprocess.run(
        ["git", "config", "--global", "user.name", "StackLens Demo"],
        check=False, capture_output=True,
    )
    # Ensure git's "safe.directory" exemption covers the work trees we
    # create under /tmp (otherwise newer git versions refuse to operate
    # on them because of the "dubious ownership" check).
    subprocess.run(
        ["git", "config", "--global", "--add", "safe.directory", "*"],
        check=False, capture_output=True,
    )

    for project in PROJECTS:
        pwn = project.get("path_with_namespace")
        if not pwn:
            continue
        default_branch = project.get("default_branch", "main")
        bare_path = os.path.join(GIT_PROJECT_ROOT, f"{pwn}.git")
        if os.path.exists(os.path.join(bare_path, "HEAD")):
            # Already initialised on a previous run.
            continue
        os.makedirs(bare_path, exist_ok=True)
        subprocess.run(
            ["git", "init", "--bare", "--initial-branch", default_branch, bare_path],
            check=True, capture_output=True,
        )
        # If `--initial-branch` isn't supported (older git) fall back to
        # symbolic-ref so the default branch exists immediately.
        subprocess.run(
            ["git", "symbolic-ref", "HEAD", f"refs/heads/{default_branch}"],
            cwd=bare_path, check=False, capture_output=True,
        )

        files = PROJECT_FILES.get(project["id"], {})
        if not files:
            # Project has no tracked files — still create an empty commit
            # so `git clone` succeeds and a branch is advertised.
            work_path = f"/tmp/stacklens-bare-init-{project['id']}"
            subprocess.run(["rm", "-rf", work_path], check=False)
            os.makedirs(work_path, exist_ok=True)
            subprocess.run(["git", "init", "--initial-branch", default_branch, work_path],
                           check=True, capture_output=True)
            subprocess.run(["git", "commit", "--allow-empty", "-m", "Initial commit"],
                           cwd=work_path, check=True, capture_output=True,
                           env={**os.environ, "GIT_AUTHOR_NAME": "StackLens Demo",
                                "GIT_AUTHOR_EMAIL": "demo@stacklens.local",
                                "GIT_COMMITTER_NAME": "StackLens Demo",
                                "GIT_COMMITTER_EMAIL": "demo@stacklens.local"})
            subprocess.run(["git", "remote", "add", "origin", bare_path],
                           cwd=work_path, check=True, capture_output=True)
            subprocess.run(["git", "push", "-u", "origin", default_branch],
                           cwd=work_path, check=True, capture_output=True)
            subprocess.run(["rm", "-rf", work_path], check=False)
            print(f"[fake-gitlab]   + bare repo (empty)  {bare_path}")
            continue

        # Build a temporary work tree, write files, commit, push to bare.
        work_path = f"/tmp/stacklens-bare-init-{project['id']}"
        subprocess.run(["rm", "-rf", work_path], check=False)
        os.makedirs(work_path, exist_ok=True)
        subprocess.run(["git", "init", "--initial-branch", default_branch, work_path],
                       check=True, capture_output=True)
        for file_path, content in files.items():
            full_path = os.path.join(work_path, file_path)
            os.makedirs(os.path.dirname(full_path), exist_ok=True)
            with open(full_path, "w", encoding="utf-8") as f:
                f.write(content)
        subprocess.run(["git", "add", "."], cwd=work_path, check=True, capture_output=True)
        subprocess.run(["git", "commit", "-m", "Initial commit"],
                       cwd=work_path, check=True, capture_output=True,
                       env={**os.environ, "GIT_AUTHOR_NAME": "StackLens Demo",
                            "GIT_AUTHOR_EMAIL": "demo@stacklens.local",
                            "GIT_COMMITTER_NAME": "StackLens Demo",
                            "GIT_COMMITTER_EMAIL": "demo@stacklens.local"})
        subprocess.run(["git", "remote", "add", "origin", bare_path],
                       cwd=work_path, check=True, capture_output=True)
        subprocess.run(["git", "push", "-u", "origin", default_branch],
                       cwd=work_path, check=True, capture_output=True)
        subprocess.run(["rm", "-rf", work_path], check=False)
        print(f"[fake-gitlab]   + bare repo ({len(files)} files)  {bare_path}")


if __name__ == "__main__":
    total_files = sum(len(f) for f in PROJECT_FILES.values())
    print(f"[fake-gitlab] Starting mock GitLab API on port 3080")
    print(f"[fake-gitlab] Loaded {len(PROJECTS)} projects, {total_files} files")
    _setup_bare_repos()
    # threaded=True: see comment in docker/demo-atlassian/fake-atlassian/app.py.
    app.run(host="0.0.0.0", port=3080, debug=False, threaded=True)
