# StackLens Demo MySQL Service

A Docker-based MySQL 8.0 instance pre-loaded with three databases that cover the
typical enterprise domain vocabulary StackLens is designed to search:
authentication, payment processing, session management, caching, and analytics.

## Databases

| Database       | Domain                                       | Highlights                                                  |
|----------------|----------------------------------------------|-------------------------------------------------------------|
| `ecommerce`    | Orders, payments, users, sessions, cache     | Views, stored procs, triggers incl. `sp_authenticate_user`  |
| `auth_db`      | OAuth2, JWT, roles, permissions, MFA, API keys | OAuth2 full flow tables, JWT blacklist, RBAC               |
| `analytics_db` | Event log, metrics, performance data         | Authentication metrics, payment funnel, cache hit stats      |

## Connection

| Parameter  | Value          |
|------------|----------------|
| Host       | `localhost`    |
| Port       | `3306`         |
| User       | `stacklens`    |
| Password   | `stacklens`    |

Root password: `rootpassword`

## Start

```bash
docker compose up -d
```

## StackLens Configuration

Add three **MySQL** database sources in StackLens (or import `stacklens-config-demo.json`):

| Name                    | Database       |
|-------------------------|----------------|
| Demo MySQL – ecommerce  | `ecommerce`    |
| Demo MySQL – auth_db    | `auth_db`      |
| Demo MySQL – analytics_db | `analytics_db` |

## Schema Objects for Testing

### Tables with interesting names
`users`, `user_sessions`, `user_authentication_logs`, `payment_transactions`,
`payment_methods`, `orders`, `session_cache`, `cache_eviction_log`,
`notification_queue`, `oauth2_clients`, `oauth2_access_tokens`,
`jwt_blacklist`, `mfa_configurations`, `api_keys`, `authentication_attempts`

### Views
`active_user_sessions`, `pending_payment_orders`, `order_payment_summary`,
`session_cache_stats`, `active_api_keys`, `user_authentication_summary`,
`daily_authentication_report`, `payment_conversion_funnel`

### Stored Procedures / Functions
`sp_authenticate_user`, `sp_process_payment`, `sp_invalidate_session`,
`fn_get_session_cache_value`, `sp_validate_oauth2_token`,
`sp_revoke_all_user_tokens`, `fn_calculate_order_total`

### Indexes
`idx_users_authentication_token`, `idx_session_cache_key`,
`idx_payment_status_created`, `idx_jwt_blacklist_jti`,
`idx_oauth2_access_token_hash`

### Triggers
`trg_after_payment_completed`, `trg_before_user_authentication`,
`trg_after_session_cache_expire`
