# Demo: Fake Backend API (k6 Load Test Target)

Simulates a typical **PHP/Laravel e-commerce application** that the StackLens
Lasttest-Analyse feature analyses and generates k6 scripts against.

## Starten

```bash
docker compose up -d
```

The API is available at **http://localhost:8085**.

## Endpoints

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| `POST` | `/api/auth/login` | – | Returns Bearer token |
| `POST` | `/api/auth/logout` | ✓ | Invalidates token |
| `GET` | `/api/users/me` | ✓ | Current user info |
| `GET` | `/api/categories` | – | All categories |
| `GET` | `/api/products` | – | **⚠ Slow without `?page=&per_page=`** |
| `GET` | `/api/products/{id}` | – | Single product |
| `GET` | `/api/orders` | ✓ | **⚠ N+1 query pattern** |
| `GET` | `/api/orders/{id}` | ✓ | Single order with items |
| `POST` | `/api/orders` | ✓ | Place new order |
| `GET` | `/api/cart` | ✓ | Current cart |
| `POST` | `/api/cart/items` | ✓ | Add item to cart |
| `DELETE` | `/api/cart` | ✓ | Clear cart |
| `GET` | `/api/stats/summary` | ✓ | **⚠ Heavy aggregation, no cache** |
| `GET` | `/health` | – | Health check |

## Demo Login

All 50 demo users can log in with **any password**:

```bash
curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "demo"}' | jq .
```

Use the returned `token` as `Authorization: Bearer <token>` for authenticated endpoints.

## Eingebaute Bottlenecks

Die App enthält absichtlich typische Backend-Performance-Probleme, die das k6-Skript aufdecken soll:

| # | Problem | Endpoint |
|---|---------|----------|
| 1 | **Fehlende Paginierung** – gibt alle 180 Produkte zurück, simuliert Full-Table-Scan + ORM-Hydration | `GET /api/products` (ohne `?page=`) |
| 2 | **N+1-Query-Pattern** – eine DB-Query pro Order für die Items | `GET /api/orders` |
| 3 | **Fehlender Session-Index** – lineare Suche über alle aktiven Tokens | Alle Auth-Requests |
| 4 | **Unkaskadiertes Aggregat** – Report-Query ohne Cache | `GET /api/stats/summary` |

## k6-Beispielaufruf

```bash
k6 run --vus 20 --duration 30s script.js
```

Erwartetes Ergebnis:
- `GET /api/products` ohne Pagination: p95 ca. 550 ms
- `GET /api/products?page=1&per_page=20`: p95 ca. 40 ms  
- `GET /api/orders`: p95 ca. 240 ms (N+1, 20 Orders × 12 ms)
- `GET /api/stats/summary`: p95 ca. 550 ms
