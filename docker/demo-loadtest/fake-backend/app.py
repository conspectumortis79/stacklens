"""
Fake Backend API
================
Simulates a typical PHP/Laravel e-commerce application for k6 load testing.

Deliberately introduces common bottlenecks so that the k6 tests generated
by StackLens's AI analysis can actually reveal them:

  Bottleneck 1 – No pagination on /api/products (full table scan)
  Bottleneck 2 – N+1 pattern on /api/orders (order items fetched per order)
  Bottleneck 3 – Linear session lookup (no index on token column)
  Bottleneck 4 – Heavy aggregation on /api/stats/summary

Endpoints:
  POST /api/auth/login              → returns bearer token
  POST /api/auth/logout             → invalidates token
  GET  /api/users/me                → current user (requires auth)

  GET  /api/products                → list all products (NO pagination by default → slow!)
  GET  /api/products?page=&per_page=→ paginated
  GET  /api/products?category=      → filtered by category
  GET  /api/products/{id}           → single product

  GET  /api/categories              → list all categories

  GET  /api/orders                  → orders for authenticated user
  GET  /api/orders/{id}             → single order (with items)
  POST /api/orders                  → place new order

  GET  /api/stats/summary           → heavy aggregation endpoint

  GET  /health                      → health check
"""

import time
import random
import math
from flask import Flask, request, jsonify, abort
from flask_cors import CORS
from data import USERS, TOKEN_MAP, PRODUCTS, PRODUCT_MAP, CATEGORIES, ORDERS, ORDER_MAP

app = Flask(__name__)
CORS(app)

# ── Active sessions (in-memory, simulates DB session table without index) ──────
# key = token string (unsorted list scan = O(n) ← intentional bottleneck)
_active_tokens = set(u["token"] for u in USERS)   # all users pre-logged-in
_next_order_id = [max(ORDER_MAP) + 1]


# ── Auth helpers ───────────────────────────────────────────────────────────────

def _get_current_user():
    """Linear scan over active tokens – simulates missing DB index (bottleneck)."""
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth[7:]
    # Simulate a slow unindexed lookup (O(n) token scan)
    time.sleep(0.005 * len(_active_tokens) / 100)   # grows with session count
    if token not in _active_tokens:
        return None
    return TOKEN_MAP.get(token)


def _require_auth():
    user = _get_current_user()
    if not user:
        abort(401, description="Unauthorized – provide a valid Bearer token.")
    return user


# ── Auth routes ────────────────────────────────────────────────────────────────

@app.post("/api/auth/login")
def login():
    body = request.get_json(silent=True) or {}
    username = body.get("username", "")
    password = body.get("password", "")  # demo: any password accepted
    # Simulate bcrypt verification delay
    time.sleep(random.uniform(0.08, 0.15))
    user = next((u for u in USERS if u["username"] == username), None)
    if not user:
        return jsonify({"error": "Invalid credentials"}), 401
    _active_tokens.add(user["token"])
    return jsonify({
        "token": user["token"],
        "token_type": "Bearer",
        "expires_in": 3600,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "email": user["email"],
            "display_name": user["display_name"],
            "role": user["role"],
        },
    })


@app.post("/api/auth/logout")
def logout():
    auth = request.headers.get("Authorization", "")
    token = auth[7:] if auth.startswith("Bearer ") else ""
    _active_tokens.discard(token)
    return jsonify({"message": "Logged out successfully"})


@app.get("/api/users/me")
def me():
    user = _require_auth()
    return jsonify({
        "id": user["id"],
        "username": user["username"],
        "email": user["email"],
        "display_name": user["display_name"],
        "role": user["role"],
    })


# ── Category routes ────────────────────────────────────────────────────────────

@app.get("/api/categories")
def list_categories():
    return jsonify({"data": CATEGORIES, "total": len(CATEGORIES)})


# ── Product routes ─────────────────────────────────────────────────────────────

@app.get("/api/products")
def list_products():
    """
    BOTTLENECK: Default behaviour returns ALL products without pagination.
    This simulates a PHP controller that forgot to add LIMIT/OFFSET.
    Artificial sleep simulates the full-table scan + PHP object hydration time.
    """
    qs = request.args
    page     = int(qs.get("page", 0))       # 0 = no pagination (default, slow)
    per_page = int(qs.get("per_page", 0))
    category = qs.get("category", "").lower()
    search   = qs.get("q", "").lower()

    active = [p for p in PRODUCTS if p["is_active"]]

    if category:
        active = [p for p in active if p["category"].lower() == category]
    if search:
        active = [p for p in active if search in p["name"].lower() or search in p["description"].lower()]

    # Simulated cost:
    # - No pagination → sleep proportional to result size (table scan + hydration)
    # - With pagination → fast
    if page == 0 or per_page == 0:
        time.sleep(0.003 * len(active))   # ~540ms for 180 active products (SLOW)
        return jsonify({
            "data": active,
            "total": len(active),
            "paginated": False,
            "warning": "No pagination applied – this endpoint is slow for large catalogues.",
        })

    total = len(active)
    total_pages = math.ceil(total / per_page)
    page = max(1, min(page, total_pages))
    start = (page - 1) * per_page
    data  = active[start: start + per_page]

    time.sleep(random.uniform(0.015, 0.04))   # fast indexed query
    return jsonify({
        "data": data,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": total_pages,
        "paginated": True,
    })


@app.get("/api/products/<int:product_id>")
def get_product(product_id):
    time.sleep(random.uniform(0.008, 0.025))
    p = PRODUCT_MAP.get(product_id)
    if not p:
        return jsonify({"error": "Product not found"}), 404
    return jsonify(p)


# ── Order routes ───────────────────────────────────────────────────────────────

@app.get("/api/orders")
def list_orders():
    """
    BOTTLENECK: N+1 pattern – fetches orders then loads items in a loop.
    Artificial per-item sleep simulates PHP ORM lazy loading.
    """
    user = _require_auth()
    qs = request.args
    page     = int(qs.get("page", 1))
    per_page = int(qs.get("per_page", 20))

    user_orders = [o for o in ORDERS if o["user_id"] == user["id"]]
    total = len(user_orders)
    start = (page - 1) * per_page
    page_orders = user_orders[start: start + per_page]

    # N+1: simulate separate query per order to load its items
    result = []
    for o in page_orders:
        time.sleep(0.012)   # per-order "SELECT items WHERE order_id = ?" ← N+1
        result.append({
            "id": o["id"],
            "status": o["status"],
            "item_count": o["item_count"],
            "total": o["total"],
            "created_at": o["created_at"],
        })

    return jsonify({
        "data": result,
        "total": total,
        "page": page,
        "per_page": per_page,
        "total_pages": math.ceil(total / per_page) if per_page else 1,
    })


@app.get("/api/orders/<int:order_id>")
def get_order(order_id):
    user = _require_auth()
    time.sleep(random.uniform(0.015, 0.035))
    o = ORDER_MAP.get(order_id)
    if not o:
        return jsonify({"error": "Order not found"}), 404
    if o["user_id"] != user["id"] and user["role"] != "ADMIN":
        return jsonify({"error": "Forbidden"}), 403
    return jsonify(o)


@app.post("/api/orders")
def create_order():
    user = _require_auth()
    body = request.get_json(silent=True) or {}
    items_in = body.get("items", [])
    if not items_in:
        return jsonify({"error": "No items provided"}), 400

    # Simulate validation + DB insert delay
    time.sleep(random.uniform(0.05, 0.12))

    items_out = []
    total = 0.0
    for it in items_in:
        pid = int(it.get("product_id", 0))
        qty = max(1, int(it.get("quantity", 1)))
        p   = PRODUCT_MAP.get(pid)
        if not p:
            return jsonify({"error": f"Product {pid} not found"}), 404
        subtotal = round(p["price"] * qty, 2)
        items_out.append({
            "product_id": pid,
            "product_name": p["name"],
            "quantity": qty,
            "unit_price": p["price"],
            "subtotal": subtotal,
        })
        total += subtotal

    oid = _next_order_id[0]
    _next_order_id[0] += 1
    new_order = {
        "id": oid,
        "user_id": user["id"],
        "status": "PENDING",
        "items": items_out,
        "item_count": len(items_out),
        "total": round(total, 2),
        "shipping_address": body.get("shipping_address", {}),
        "created_at": "2026-04-03T10:00:00Z",
        "updated_at": "2026-04-03T10:00:00Z",
    }
    ORDER_MAP[oid] = new_order
    ORDERS.append(new_order)
    return jsonify(new_order), 201


# ── Stats route ────────────────────────────────────────────────────────────────

@app.get("/api/stats/summary")
def stats_summary():
    """
    BOTTLENECK: Heavy in-memory aggregation simulates an un-cached PHP
    report query that runs a full scan over orders and products each time.
    """
    _require_auth()
    time.sleep(random.uniform(0.25, 0.55))   # heavy aggregation (no cache)

    total_revenue = sum(o["total"] for o in ORDERS)
    by_status = {}
    for o in ORDERS:
        by_status[o["status"]] = by_status.get(o["status"], 0) + 1

    top_products = {}
    for o in ORDERS:
        for it in o["items"]:
            pid = it["product_id"]
            top_products[pid] = top_products.get(pid, 0) + it["quantity"]
    top_5 = sorted(top_products.items(), key=lambda x: x[1], reverse=True)[:5]

    return jsonify({
        "total_orders": len(ORDERS),
        "total_revenue": round(total_revenue, 2),
        "orders_by_status": by_status,
        "top_products": [
            {"product_id": pid, "name": PRODUCT_MAP[pid]["name"], "units_sold": qty}
            for pid, qty in top_5
        ],
        "active_products": sum(1 for p in PRODUCTS if p["is_active"]),
        "total_categories": len(CATEGORIES),
        "cached": False,
        "note": "This report runs a full aggregation on every request (no cache).",
    })


# ── Cart simulation ────────────────────────────────────────────────────────────

_carts = {}   # user_id → [items]

@app.get("/api/cart")
def get_cart():
    user = _require_auth()
    time.sleep(random.uniform(0.01, 0.03))
    cart = _carts.get(user["id"], [])
    total = sum(it["subtotal"] for it in cart)
    return jsonify({"items": cart, "item_count": len(cart), "total": round(total, 2)})


@app.post("/api/cart/items")
def add_to_cart():
    user = _require_auth()
    body = request.get_json(silent=True) or {}
    pid  = int(body.get("product_id", 0))
    qty  = max(1, int(body.get("quantity", 1)))
    p    = PRODUCT_MAP.get(pid)
    if not p:
        return jsonify({"error": "Product not found"}), 404
    time.sleep(random.uniform(0.02, 0.06))
    cart = _carts.setdefault(user["id"], [])
    for it in cart:
        if it["product_id"] == pid:
            it["quantity"] += qty
            it["subtotal"] = round(it["quantity"] * it["unit_price"], 2)
            break
    else:
        cart.append({
            "product_id": pid,
            "product_name": p["name"],
            "quantity": qty,
            "unit_price": p["price"],
            "subtotal": round(qty * p["price"], 2),
        })
    return jsonify({"items": cart, "item_count": len(cart)}), 201


@app.delete("/api/cart")
def clear_cart():
    user = _require_auth()
    _carts.pop(user["id"], None)
    return jsonify({"message": "Cart cleared"}), 200


# ── Health ─────────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return jsonify({
        "status": "ok",
        "service": "fake-backend",
        "version": "1.0.0",
        "endpoints": [
            "POST /api/auth/login",
            "POST /api/auth/logout",
            "GET  /api/users/me",
            "GET  /api/categories",
            "GET  /api/products",
            "GET  /api/products/{id}",
            "GET  /api/orders",
            "GET  /api/orders/{id}",
            "POST /api/orders",
            "GET  /api/cart",
            "POST /api/cart/items",
            "DELETE /api/cart",
            "GET  /api/stats/summary",
        ],
    })


if __name__ == "__main__":
    # threaded=True: see comment in docker/demo-atlassian/fake-atlassian/app.py.
    app.run(host="0.0.0.0", port=8085, debug=False, threaded=True)
