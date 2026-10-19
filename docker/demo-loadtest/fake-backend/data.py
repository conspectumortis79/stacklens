"""
Seed data for the fake backend – simulates an e-commerce PHP application.
Deliberately contains data patterns that a load-test analysis would flag:
  - Large product lists without pagination (N+1 risk)
  - Orders that join multiple tables (slow aggregation)
  - Session lookup that has no index (linear scan)
"""

import random
from typing import Any

# ── Users ─────────────────────────────────────────────────────────────────────

USERS: list[dict[str, Any]] = [
    {
        "id": i,
        "username": f"user{i}",
        "email": f"user{i}@demo.example.com",
        "display_name": f"Demo User {i}",
        "role": "ADMIN" if i == 1 else "USER",
        "token": f"tok_{i:04d}_demo",
    }
    for i in range(1, 51)
]

# token → user lookup (simulates session table without index → slow!)
TOKEN_MAP: dict[str, dict[str, Any]] = {u["token"]: u for u in USERS}

# ── Categories ────────────────────────────────────────────────────────────────

CATEGORIES: list[dict[str, Any]] = [
    {"id": 1, "name": "Electronics", "slug": "electronics"},
    {"id": 2, "name": "Clothing",    "slug": "clothing"},
    {"id": 3, "name": "Books",       "slug": "books"},
    {"id": 4, "name": "Home & Garden","slug": "home-garden"},
    {"id": 5, "name": "Sports",      "slug": "sports"},
    {"id": 6, "name": "Toys",        "slug": "toys"},
]

# ── Products ──────────────────────────────────────────────────────────────────

PRODUCT_NAMES = [
    "Wireless Keyboard", "USB-C Hub", "Laptop Stand", "Monitor 27\"",
    "Mechanical Keyboard", "Gaming Mouse", "Webcam 1080p", "Headset Pro",
    "T-Shirt Cotton", "Jeans Slim Fit", "Running Shoes", "Winter Jacket",
    "Python Programming", "Clean Code", "System Design", "Algorithms",
    "Coffee Table", "Desk Lamp", "Plant Pot Set", "Wall Clock",
    "Yoga Mat", "Dumbbells 10kg", "Resistance Bands", "Jump Rope",
    "LEGO City Set", "Board Game EU", "Remote Car", "Puzzle 1000pcs",
]

random.seed(42)

PRODUCTS: list[dict[str, Any]] = [
    {
        "id": i,
        "name": PRODUCT_NAMES[(i - 1) % len(PRODUCT_NAMES)] + (f" v{(i-1)//len(PRODUCT_NAMES)+1}" if i > len(PRODUCT_NAMES) else ""),
        "sku": f"SKU-{i:05d}",
        "category_id": ((i - 1) % len(CATEGORIES)) + 1,
        "category": CATEGORIES[((i - 1) % len(CATEGORIES))]["name"],
        "price": round(random.uniform(4.99, 599.99), 2),
        "stock": random.randint(0, 500),
        "is_active": i % 10 != 0,  # 10% inactive
        "rating": round(random.uniform(1.0, 5.0), 1),
        "review_count": random.randint(0, 3400),
        "description": (
            f"High-quality product #{i}. "
            "This item belongs to our best-selling range and ships within 2 business days. "
            "Performance tested and certified."
        ),
        "image_url": f"https://demo.example.com/images/product-{i}.jpg",
        "created_at": f"2024-{(i % 12)+1:02d}-{(i % 28)+1:02d}T10:00:00Z",
    }
    for i in range(1, 201)   # 200 products → realistic for pagination tests
]

PRODUCT_MAP: dict[int, dict[str, Any]] = {p["id"]: p for p in PRODUCTS}

# ── Orders ────────────────────────────────────────────────────────────────────

ORDER_STATUSES = ["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"]

ORDERS: list[dict[str, Any]] = []
for oid in range(1, 301):   # 300 orders
    user_id = random.randint(1, 50)
    n_items = random.randint(1, 6)
    items: list[dict[str, Any]] = []
    total: float = 0.0
    for _ in range(n_items):
        pid = random.randint(1, 200)
        qty = random.randint(1, 4)
        price: float = float(PRODUCT_MAP[pid]["price"])
        items.append({"product_id": pid, "product_name": PRODUCT_MAP[pid]["name"],
                       "quantity": qty, "unit_price": price,
                       "subtotal": round(qty * price, 2)})
        total += qty * price
    ORDERS.append({
        "id": oid,
        "user_id": user_id,
        "status": ORDER_STATUSES[oid % len(ORDER_STATUSES)],
        "items": items,
        "item_count": n_items,
        "total": round(total, 2),
        "shipping_address": {
            "street": f"{oid} Demo Street",
            "city": "Berlin",
            "zip": f"1{oid:04d}",
            "country": "DE",
        },
        "created_at": f"2025-{(oid % 12)+1:02d}-{(oid % 28)+1:02d}T14:30:00Z",
        "updated_at": f"2025-{(oid % 12)+1:02d}-{(oid % 28)+1:02d}T15:00:00Z",
    })

ORDER_MAP: dict[int, dict[str, Any]] = {o["id"]: o for o in ORDERS}
