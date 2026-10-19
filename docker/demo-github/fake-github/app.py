"""
Fake GitHub API Server
Simulates GitHub REST API v3 for StackLens testing.

Endpoints:
  GET  /health                                          – healthcheck
  GET  /user                                            – authenticated user
  GET  /repos/<owner>/<repo>                            – repository info
  GET  /repos/<owner>/<repo>/pulls                      – list pull requests (?state=open|closed|all)
  GET  /repos/<owner>/<repo>/pulls/<number>             – single pull request
  GET  /repos/<owner>/<repo>/pulls/<number>/files       – files changed in PR
  GET  /repos/<owner>/<repo>/git/ref/<ref>              – resolve a git ref → SHA
  POST /repos/<owner>/<repo>/git/refs                   – create a new ref (in-memory)
  GET  /repos/<owner>/<repo>/contents/<path>            – raw file contents (base64)
  PUT  /repos/<owner>/<repo>/contents/<path>            – create / update a file (in-memory)
  POST /repos/<owner>/<repo>/pulls                      – create a pull request (in-memory)
  GET  /search/code                                     – search code (?q=…)
  GET  /search/issues                                   – search issues/PRs (?q=…)

Authentication: any Bearer token or Basic credentials are accepted — no validation.
"""

import base64
import json
import re
import time
from flask import Flask, Response, jsonify, request
from flask_cors import CORS
from data import (
    OWNER,
    REPOS,
    PULL_REQUESTS,
    PR_FILES,
    REPO_FILES,
    CODE_SEARCH_INDEX,
    ISSUE_SEARCH_INDEX,
)

app = Flask(__name__)
CORS(app)

# ── In-memory write stores ────────────────────────────────────────────────────
_created_refs: dict[str, dict[str, str]] = {}   # "{owner}/{repo}" -> {ref: sha}
_created_files: dict[str, dict] = {}             # "{owner}/{repo}/{path}" -> {content, sha, message}
_created_prs: list[dict] = []
_pr_counter = {"n": 100}


# ── Helpers ───────────────────────────────────────────────────────────────────

def _find_repo(owner: str, repo: str) -> dict | None:
    key = f"{owner}/{repo}"
    for r in REPOS:
        if r["full_name"].lower() == key.lower():
            return r
    return None


def _keyword_match(text: str, query: str) -> bool:
    """True if all words in query appear (case-insensitive) somewhere in text."""
    text_lower = text.lower()
    # Strip GitHub search qualifiers like repo:owner/name, language:kotlin
    words = [w for w in re.split(r"[\s+]+", query) if w and ":" not in w]
    return all(w.lower() in text_lower for w in words) if words else True


def _rate_limit_headers() -> dict:
    return {
        "X-RateLimit-Limit": "60",
        "X-RateLimit-Remaining": "59",
        "X-RateLimit-Reset": str(int(time.time()) + 3600),
    }


def _json(data, status=200) -> Response:
    resp = jsonify(data)
    resp.status_code = status
    for k, v in _rate_limit_headers().items():
        resp.headers[k] = v
    return resp


# ── Health / Auth ─────────────────────────────────────────────────────────────

@app.route("/health")
def health():
    return _json({"status": "ok", "service": "fake-github"})


@app.route("/user")
def get_user():
    return _json({
        "login": "demo-user",
        "id": 1,
        "name": "Demo User",
        "email": "demo@demo-org.local",
        "type": "User",
        "site_admin": False,
        "html_url": "http://localhost:8082/demo-user",
    })


# ── Repositories ──────────────────────────────────────────────────────────────

@app.route("/repos/<owner>/<repo>")
def get_repo(owner, repo):
    r = _find_repo(owner, repo)
    if not r:
        return _json({"message": "Not Found"}, 404)
    return _json(r)


# ── Pull Requests ──────────────────────────────────────────────────────────────

@app.route("/repos/<owner>/<repo>/pulls")
def list_pulls(owner, repo):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    state = request.args.get("state", "open")
    prs = list(PULL_REQUESTS.get(repo, [])) + [
        p for p in _created_prs if p.get("_repo") == repo and p.get("_owner") == owner
    ]
    if state != "all":
        prs = [p for p in prs if p["state"] == state]
    return _json(prs)


@app.route("/repos/<owner>/<repo>/pulls/<int:number>")
def get_pull(owner, repo, number):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    all_prs = list(PULL_REQUESTS.get(repo, [])) + _created_prs
    for p in all_prs:
        if p["number"] == number:
            return _json(p)
    return _json({"message": "Not Found"}, 404)


@app.route("/repos/<owner>/<repo>/pulls/<int:number>/files")
def get_pull_files(owner, repo, number):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    files = PR_FILES.get((repo, number), [])
    return _json(files)


@app.route("/repos/<owner>/<repo>/pulls", methods=["POST"])
def create_pull(owner, repo):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    body = request.json or {}
    _pr_counter["n"] += 1
    pr = {
        "number": _pr_counter["n"],
        "title": body.get("title", "Demo PR"),
        "state": "open",
        "body": body.get("body", ""),
        "user": {"login": "demo-user"},
        "head": {"ref": body.get("head", "feature-branch"), "sha": "demosha123456789"},
        "base": {"ref": body.get("base", "main"), "sha": "000feedbeef000"},
        "html_url": f"http://localhost:8082/{owner}/{repo}/pull/{_pr_counter['n']}",
        "created_at": "2026-05-25T00:00:00Z",
        "updated_at": "2026-05-25T00:00:00Z",
        "draft": body.get("draft", False),
        "labels": [],
        "_repo": repo,
        "_owner": owner,
    }
    _created_prs.append(pr)
    return _json(pr, 201)


# ── Git refs ──────────────────────────────────────────────────────────────────

@app.route("/repos/<owner>/<repo>/git/ref/<path:ref>")
def get_ref(owner, repo, ref):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    key = f"{owner}/{repo}"
    sha = _created_refs.get(key, {}).get(ref, "000feedbeef000abcdef1234567890ab")
    return _json({
        "ref": f"refs/{ref}",
        "object": {"sha": sha, "type": "commit"},
    })


@app.route("/repos/<owner>/<repo>/git/refs", methods=["POST"])
def create_ref(owner, repo):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    body = request.json or {}
    ref = body.get("ref", "refs/heads/demo-branch").lstrip("refs/")
    sha = body.get("sha", "newsha123456789abcdef")
    key = f"{owner}/{repo}"
    if key not in _created_refs:
        _created_refs[key] = {}
    _created_refs[key][ref] = sha
    return _json({
        "ref": body.get("ref", f"refs/heads/{ref}"),
        "object": {"sha": sha, "type": "commit"},
    }, 201)


# ── File contents ──────────────────────────────────────────────────────────────

@app.route("/repos/<owner>/<repo>/contents/<path:path>")
def get_contents(owner, repo, path):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    store_key = f"{owner}/{repo}/{path}"
    # Check in-memory writes first
    if store_key in _created_files:
        entry = _created_files[store_key]
        return _json({
            "name": path.split("/")[-1],
            "path": path,
            "sha": entry["sha"],
            "content": entry["content"],
            "encoding": "base64",
            "html_url": f"http://localhost:8082/{owner}/{repo}/blob/main/{path}",
        })
    # Static demo files
    content = REPO_FILES.get(repo, {}).get(path)
    if content is None:
        return _json({"message": "Not Found"}, 404)
    encoded = base64.b64encode(content.encode()).decode()
    return _json({
        "name": path.split("/")[-1],
        "path": path,
        "sha": "fakecontent" + str(hash(content) % 100000),
        "content": encoded,
        "encoding": "base64",
        "html_url": f"http://localhost:8082/{owner}/{repo}/blob/main/{path}",
    })


@app.route("/repos/<owner>/<repo>/contents/<path:path>", methods=["PUT"])
def put_contents(owner, repo, path):
    if not _find_repo(owner, repo):
        return _json({"message": "Not Found"}, 404)
    body = request.json or {}
    sha = "newfilesha" + str(int(time.time()))
    store_key = f"{owner}/{repo}/{path}"
    _created_files[store_key] = {
        "content": body.get("content", ""),
        "sha": sha,
        "message": body.get("message", ""),
    }
    return _json({
        "content": {"path": path, "sha": sha},
        "commit": {"sha": "commitsha" + str(int(time.time())), "message": body.get("message", "")},
    }, 201)


# ── Search ────────────────────────────────────────────────────────────────────

@app.route("/search/code")
def search_code():
    query = request.args.get("q", "")
    results = []
    for item in CODE_SEARCH_INDEX:
        if _keyword_match(item["content"] + " " + item["path"], query):
            repo_name = item["repo"]
            repo = next((r for r in REPOS if r["name"] == repo_name), None)
            results.append({
                "name": item["path"].split("/")[-1],
                "path": item["path"],
                "sha": "searchsha" + str(hash(item["path"]) % 100000),
                "html_url": f"http://localhost:8082/{OWNER}/{repo_name}/blob/main/{item['path']}",
                "repository": repo or {"full_name": f"{OWNER}/{repo_name}"},
                "text_matches": [{"fragment": item["content"][:200]}],
            })
    return _json({
        "total_count": len(results),
        "incomplete_results": False,
        "items": results,
    })


@app.route("/search/issues")
def search_issues():
    query = request.args.get("q", "")
    results = []
    for item in ISSUE_SEARCH_INDEX:
        if _keyword_match(item["title"] + " " + item["body"], query):
            results.append({
                "number": item["number"],
                "title": item["title"],
                "state": item["state"],
                "body": item["body"],
                "user": item["user"],
                "html_url": item["html_url"],
                "created_at": item["created_at"],
                "pull_request": {"url": item["html_url"]} if item["type"] == "pull_request" else None,
                "repository_url": f"http://localhost:8082/repos/{OWNER}/{item['repo']}",
            })
    return _json({
        "total_count": len(results),
        "incomplete_results": False,
        "items": results,
    })


if __name__ == "__main__":
    # threaded=True: see comment in docker/demo-atlassian/fake-atlassian/app.py.
    app.run(host="0.0.0.0", port=8082, debug=False, threaded=True)
