# Security Notes

This repository is public, so API endpoint paths must not be treated as secrets. Production safety depends on authentication, authorization, network controls, and secret rotation.

## Admin, Ops, And Dev APIs

Internal console APIs under `/api/admin/**`, `/api/ops/**`, and `/api/dev/**` are guarded by an application-level exposure filter.

- In the default local profile, the console API is enabled so developers can test it locally.
- In the `prod` profile, the console API is disabled by default and returns `404` unless explicitly enabled.
- If the console API is enabled in `prod`, an IP allowlist is required by default.
- `X-Forwarded-For` is trusted only when the request comes from a configured trusted proxy.
- Generated OpenAPI paths exclude internal console APIs by default.

Production environment variables:

| Variable | Required | Purpose |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE=prod` | Yes | Enables production defaults. |
| `ADMIN_CONSOLE_ENABLED=true` | Only for console deployments | Explicitly exposes internal console APIs. Omit or set `false` for public app-only deployments. |
| `ADMIN_CONSOLE_REQUIRE_ALLOWLIST=true` | Yes when console is enabled | Keeps the network allowlist mandatory. |
| `ADMIN_CONSOLE_ALLOWED_IP_RANGES` | Yes when console is enabled | Comma-separated exact IPs or CIDR ranges, such as `203.0.113.10,198.51.100.0/24`. |
| `ADMIN_CONSOLE_TRUSTED_PROXY_ADDRESSES` | When behind reverse proxy | Comma-separated reverse proxy IPs allowed to supply `X-Forwarded-For`. |

Recommended production posture:

1. Keep admin, ops, and dev APIs disabled on the public mobile API deployment.
2. If the admin web console needs access, expose it only through VPN, private subnet, bastion, or a provider security group.
3. Set `ADMIN_CONSOLE_ALLOWED_IP_RANGES` to the egress IPs of that private access path.
4. Do not seed operator accounts or credentials in tracked files.
5. Rotate `JWT_SECRET`, database credentials, and operator credentials if a deployment environment or repository secret is suspected to be exposed.

## Public API Contract

Public mobile API paths are intentionally stable for client compatibility. Security changes should preserve endpoint paths, methods, and DTO fields unless an ADR and client migration plan are created first.
