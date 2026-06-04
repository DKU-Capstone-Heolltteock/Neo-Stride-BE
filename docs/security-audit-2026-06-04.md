# Neo-Stride Backend Security Audit - 2026-06-04

## Executive summary

This audit reviewed the Spring Boot backend, database access layer, upload handling, Docker/container setup, CI/CD workflow, and observable host configuration for authentication, authorization, secrets, injection risks, file handling, network exposure, and operational hardening.

The highest-risk issues found were object-level authorization gaps in notification APIs, an ownership bug in community content deletion, and a tracked SQL backup containing sensitive GPS trace data. Code fixes were applied for the authorization issues, the sensitive backup was removed from the working tree and future `backups/` content is ignored, rate-limit coverage was broadened, and embedded Tomcat was overridden to a patched version. Host-level hardening items are documented separately because they require operational changes outside the application commit.

Validation passed after the fixes:

- `docker run --rm -u 1001:1001 -v /home/ubuntu/neo-stride/backend:/workspace -w /workspace -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 -e MAVEN_OPTS=-Duser.home=/tmp maven:3.9.11-eclipse-temurin-21 mvn -B -ntp test`
- Result: 214 tests, 0 failures, 0 errors, 0 skipped.
- `docker run --rm -u 1001:1001 -v /home/ubuntu/neo-stride/backend:/workspace -w /workspace -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 -e MAVEN_OPTS=-Duser.home=/tmp maven:3.9.11-eclipse-temurin-21 mvn -B -ntp dependency:tree -Dincludes=org.apache.tomcat.embed`
- Result: `tomcat-embed-core`, `tomcat-embed-el`, and `tomcat-embed-websocket` resolve to 11.0.22.
- `git diff --check`
- Result: clean.

External references used:

- [OWASP API1:2023 Broken Object Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
- [Apache Tomcat 11.x security advisories](https://tomcat.apache.org/security-11.html)
- [Docker Linux post-installation security note](https://docs.docker.com/engine/install/linux-postinstall/)
- [Docker Engine security documentation](https://docs.docker.com/engine/security/)

## Scope and method

Reviewed areas:

- Authentication and authorization flows, including bearer-token handling and object ownership checks.
- JWT and password handling.
- Environment variable and secret patterns.
- REST endpoints, request headers, DTO handling, and upload paths.
- JDBC SQL construction, dynamic predicates, and use of prepared statements.
- Command execution and path handling.
- Rate limiting and abuse controls.
- Logging and possible sensitive data exposure.
- Maven dependency graph and current known Tomcat advisories.
- Dockerfile, compose deployment, container privilege settings, and Docker daemon exposure.
- GitHub Actions deploy workflow.
- Observable host SSH, nginx/TLS, public ports, file permissions, Linux groups, and update state.

Host inspection was limited to permissions available to the `ubuntu` user. `nft list ruleset` failed with `Operation not permitted`, so raw firewall rules could not be verified without root. No secrets from `.env` were printed into this report.

## Findings by severity

### Critical

#### C-01: Sensitive GPS trace SQL backup committed to the repository

Description: A tracked SQL backup under `backups/gps_traces_before_watch_metrics_20260514_061332.sql` contained GPS trace rows. GPS traces are sensitive location data.

Impact: Anyone with repository access, including fork, CI, artifact, or clone access, could obtain historical user location data. If the repository has been shared beyond the intended maintainers, this should be treated as data exposure.

Exploitation scenario: An attacker with read access to the Git repository searches for `.sql` files, extracts the backup, and reconstructs user routes or activity patterns from GPS points.

Root cause: The repository allowed committed database backup artifacts and did not ignore `backups/`.

Remediation applied: Deleted the tracked backup from the working tree and added `backups/` to `.gitignore`. This prevents the file from being present in the current tree and reduces recurrence risk.

Residual risk: The file remains in Git history. `git log -- backups/gps_traces_before_watch_metrics_20260514_061332.sql` shows commit `a17fdbb` introduced the tracked backup. Purge the file from repository history with an approved history rewrite process and rotate or treat any affected data as exposed according to incident-response policy.

### High

#### H-01: Notification APIs allowed unauthenticated access and object-level authorization bypass

Description: The notification controller trusted an optional `X-User-Id` header with a default user ID and did not require bearer authentication. Per-notification read/delete actions accepted only `notificationId`, so an authenticated or unauthenticated caller could target another user's notification by ID.

Impact: Attackers could read notification lists for user 1 by omitting the header, mark other users' notifications as read, or delete notifications that did not belong to them.

Exploitation scenario: A client sends `DELETE /api/notifications/123` or `PATCH /api/notifications/123/read` for a guessed sequential notification ID. Before the fix, the repository mutation was scoped only by `notification_id`.

Root cause: Legacy client compatibility code treated a header as identity and repository updates/deletes did not include `user_id` ownership filters. This is a Broken Object Level Authorization pattern as described by OWASP API1:2023.

Remediation applied: `NotificationController` now requires a bearer token through `AuthenticatedUserService`, validates optional `X-User-Id` only if present, and derives identity from JWT claims. `NotificationService` and `NotificationRepository` now require `userId` for single notification read/delete operations and scope the SQL by both `user_id` and `notification_id`.

Compatibility note: API paths, methods, response shapes, and the optional `X-User-Id` header are preserved. The intentional behavioral change is that notification APIs now require valid bearer auth and reject mismatched headers.

#### H-02: Community content deletion could remove interactions for content owned by another user

Description: `CommunityRepository.deleteContent` deleted `community_interactions` by `content_id` before checking that the content belonged to the caller. The subsequent content delete had an owner filter, but the interaction delete did not.

Impact: Any authenticated user who could call the delete endpoint with another content ID could wipe likes, bookmarks, comments, or tags associated with that content even though the content row itself remained.

Exploitation scenario: User A sends `DELETE /api/community/feeds/{victimFeedId}`. The content delete fails because A is not the author, but the interaction delete has already removed related rows for the victim feed.

Root cause: Ownership was checked only on the parent content deletion, not on the dependent interaction cleanup.

Remediation applied: The interaction deletion now joins `community_contents` and requires matching `author_user_id` and `content_type` before deleting interactions. A focused repository test verifies interactions are removed only when the caller owns the content.

### Medium

#### M-01: Embedded Tomcat 11.0.21 had advisories fixed by 11.0.22

Description: Maven dependency resolution showed `tomcat-embed-core`, `tomcat-embed-el`, and `tomcat-embed-websocket` at 11.0.21 through Spring Boot 4.0.6. Apache lists multiple 11.0.21-affecting advisories fixed in 11.0.22, including moderate and low severity Tomcat issues.

Impact: Exposure depends on enabled Tomcat features. The app does not appear to use AJP, Tomcat DIGEST auth, or WebDAV, but keeping a vulnerable embedded server version increases patch latency and future risk.

Exploitation scenario: A future or enabled feature path relies on Tomcat behavior affected by one of the 11.0.21 advisories; an attacker sends crafted requests to exploit server-level parsing, authentication, or constraint behavior.

Root cause: Spring Boot dependency management had not yet advanced the managed Tomcat version from 11.0.21.

Remediation applied: Added a targeted Maven property override: `<tomcat.version>11.0.22</tomcat.version>`. Dependency resolution confirms all embedded Tomcat artifacts moved to 11.0.22, and the full test suite passes.

#### M-02: Rate limiting did not cover several write-heavy legacy and API paths

Description: The existing rate-limit filter covered auth, selected community paths, running paths, and profile image writes. Legacy `/feeds`, `/api/tips`, `/api/notifications`, `/api/coaching`, and several `/users/me` writes were not covered.

Impact: Attackers could concentrate request volume on uncovered write endpoints to increase brute force, spam, resource consumption, or notification mutation attempts.

Exploitation scenario: A client repeatedly posts to `/feeds` or deletes notifications under `/api/notifications/...` from one IP. Before the fix, those paths bypassed the write bucket.

Root cause: Path matching in `RateLimitFilter` was hard-coded and incomplete.

Remediation applied: Reworked the filter path selection into `isWriteLimitedPath` and `isReadLimitedPath`, expanding coverage to legacy and current write/read endpoints. Added a regression test that proves `/feeds` and `/api/notifications` share the write bucket.

#### M-03: Public `rpcbind` listener exposed on port 111

Description: `ss -tulpen` showed `rpcbind.socket` listening on `0.0.0.0:111` and `[::]:111` over TCP and UDP.

Impact: If NFS/RPC services are not required, exposing `rpcbind` adds unnecessary network attack surface and service fingerprinting. If vulnerable RPC-related services are later enabled, discovery becomes easier.

Exploitation scenario: An internet scanner finds port 111, queries RPC program mappings, and targets any exposed or later-enabled RPC service.

Root cause: `rpcbind.socket` is enabled and active on all interfaces.

Remediation applied: None in this commit because this is a host-level operational change. Recommended action is to disable and stop `rpcbind.socket` if not required, or restrict it at the firewall/provider security group.

#### M-04: Highly privileged Linux user groups on deployment user

Description: `id ubuntu` showed membership in `sudo`, `docker`, and `lxd`. Docker's own documentation warns that the `docker` group grants root-level privileges, and Docker daemon control can alter the host filesystem through privileged container operations.

Impact: Compromise of the `ubuntu` user is effectively host-root compromise through sudo, Docker, or LXD paths.

Exploitation scenario: An attacker obtains the `ubuntu` SSH key or shell access through an unrelated app issue, then starts a container with host filesystem bind mounts or uses sudo to persist on the host.

Root cause: Day-to-day deployment and container management run under a broad administrative account.

Remediation applied: None in this commit. Recommended action is to separate deploy/runtime users, remove unnecessary `docker`/`lxd` membership, require sudo only for narrowly scoped commands, and consider rootless Docker or a dedicated CI deploy agent.

#### M-05: nginx TLS config still includes TLS 1.0 and TLS 1.1 and HTTPS lacks HSTS

Description: `/etc/nginx/nginx.conf` contains `ssl_protocols TLSv1 TLSv1.1 TLSv1.2 TLSv1.3`. Local runtime checks succeeded for TLS 1.2 and TLS 1.3. The local OpenSSL client would not initiate TLS 1.0/1.1, so legacy negotiation could not be proven from this host. HTTPS response headers did not include `Strict-Transport-Security`.

Impact: Broad protocol configuration can re-enable legacy TLS if package or crypto policy changes. Missing HSTS allows first-visit downgrade risk when users reach the HTTP endpoint before being redirected.

Exploitation scenario: A client or intermediary that supports legacy TLS connects if the server permits it, or a network attacker interferes with an initial HTTP request before the redirect is cached by the browser.

Root cause: Default nginx TLS protocol line has not been tightened, and the site config does not set HSTS.

Remediation applied: None in this commit because nginx host changes require operational rollout. Recommended action is to set `ssl_protocols TLSv1.2 TLSv1.3;`, validate with an external TLS scanner, and add HSTS after confirming all subdomain and HTTPS requirements.

### Low

#### L-01: Container hardening can be improved

Description: The backend container runs as `spring:spring` and is not privileged, which is good. However, `docker inspect` showed `ReadonlyRootfs=false`, no explicit `CapDrop`, and no additional `SecurityOpt`. The MySQL container also runs unprivileged at the Docker level but has default image user settings.

Impact: If the app or a dependency is compromised, a writable root filesystem and default capability set can make persistence or post-exploitation easier inside the container.

Exploitation scenario: An attacker gains remote code execution in the app and writes tools or persistence under writable container paths outside the intended upload volume.

Root cause: Compose currently prioritizes functional defaults and does not include a hardening profile.

Remediation applied: None in this commit. Recommended action is to test `read_only: true`, `tmpfs` for needed temp paths, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, and explicit writable mounts for `/data/uploads` and any JVM temp directory.

#### L-02: CI/CD workflow is not fully pinned and deploys `latest`

Description: GitHub Actions uses major/version tags such as `actions/checkout@v4`, `actions/setup-java@v4`, and `appleboy/ssh-action@v1.0.3`. The deploy process pushes and pulls `latest` as well as the Git SHA image tag.

Impact: Tag-based actions may move over time, and deploying `latest` makes rollback/audit trails less deterministic than deploying an immutable digest or SHA tag.

Exploitation scenario: A compromised upstream action tag or a mistaken `latest` push causes unreviewed code to run in CI or production.

Root cause: The workflow uses common convenience tags rather than immutable action SHAs and immutable deployment references.

Remediation applied: None in this commit to avoid changing deployment behavior. Recommended action is to pin actions by full commit SHA, grant minimal workflow permissions, and deploy the `${{ github.sha }}` image tag or digest rather than `latest`.

#### L-03: Firewall state could not be fully verified from this user

Description: `ufw` was not installed or not on PATH, and `nft list ruleset` failed without root. Public listeners observed were SSH 22, HTTP 80, HTTPS 443, and RPC 111.

Impact: Without verified firewall/provider rules, host exposure may differ from intended architecture.

Exploitation scenario: An unexpected listener is added later and becomes publicly reachable because there is no explicit deny-by-default host firewall.

Root cause: Firewall verification requires root or provider-console access that was outside the current process.

Remediation applied: None in this commit. Recommended action is to enforce a provider security group or host firewall allowing only 22 from trusted admin networks, 80/443 public, and blocking 111 unless required.

#### L-04: SSH hardening can be tightened

Description: Readable SSH config shows `PasswordAuthentication no`, which is good. It also shows `X11Forwarding yes` and no explicit `PermitRootLogin no` in the visible config.

Impact: X11 forwarding is rarely needed for server deployments and increases SSH session attack surface. Explicit root-login denial reduces ambiguity across included configs and defaults.

Exploitation scenario: A compromised SSH session abuses X11 forwarding behavior, or future config changes allow root login unexpectedly because it was not pinned off.

Root cause: SSH config appears close to cloud defaults rather than a hardened production baseline.

Remediation applied: None in this commit. Recommended action is to set `X11Forwarding no`, `PermitRootLogin no`, keep `PasswordAuthentication no`, and restrict SSH source IPs at the firewall/provider layer.

#### L-05: Upload directory permissions are broader than necessary

Description: `/home/ubuntu/neo-stride/uploads` is `775` and owned by `ubuntu:ubuntu`. The backend bind-mounts it read/write as `/data/uploads`.

Impact: Any local user in the owning group can read or write uploaded content. If additional local accounts are added later, the risk grows.

Exploitation scenario: A local user or compromised process in the `ubuntu` group modifies uploaded images or plants unexpected files in upload storage.

Root cause: Shared host directory permissions are group-writable/readable rather than least privilege for the container runtime path.

Remediation applied: None in this commit. Recommended action is to use a dedicated uploads group/user and set the narrowest permissions compatible with nginx/static serving and the backend writer.

#### L-06: HEIC/HEIF uploads are validated by container brand only

Description: Upload handling validates JPEG/PNG by decoding raster content and validates WebP/HEIC/HEIF by magic bytes or ISO base media brand. HEIC/HEIF are not decoded server-side.

Impact: The extension and storage path are safe, but malformed HEIC/HEIF content could still be stored and later processed by clients or downstream tooling.

Exploitation scenario: An attacker uploads a file with a valid HEIC brand box but malformed image payload that triggers a client or image-processing bug when viewed elsewhere.

Root cause: Java ImageIO does not decode HEIC/HEIF by default, so current validation is structural rather than full decode validation.

Remediation applied: None in this commit. Recommended action is to either decode/re-encode HEIC/HEIF with a vetted library or disallow HEIC/HEIF until full content validation is available.

#### L-07: System packages have pending updates

Description: `apt list --upgradable` reported pending updates for `apparmor`, `libapparmor1`, and `cloud-init`.

Impact: Pending security or stability fixes may not be applied to the host.

Exploitation scenario: A local or cloud-init/AppArmor related vulnerability remains exploitable after a fix is available through the OS vendor.

Root cause: Host package maintenance is not fully current at the time of the audit.

Remediation applied: None in this commit. Recommended action is to run the standard patch process, reboot if required, and enable or verify unattended security upgrades.

## Scope-specific results

Authentication and authorization:

- JWT secret length and positive TTLs are enforced at startup.
- Access and refresh token types are distinguished.
- Community controllers derive user identity through `AuthenticatedUserService` and validate optional `X-User-Id` when present.
- Notification endpoints were brought in line with that pattern in this audit.
- Object ownership checks were strengthened for notification mutations and community delete cleanup.

Secret and credential management:

- Application config pulls secrets from environment variables or configtree secrets.
- `.env` is untracked and file mode is `600` on the host.
- `.dockerignore` excludes `.env`.
- Tracked grep scan found placeholders/examples only, not live API keys or private keys.
- The sensitive GPS SQL backup was the primary data-management issue and was removed from the current tree.

Environment variables:

- `DB_PASSWORD` is required in compose.
- `JWT_SECRET` defaults to empty but the app fails startup if it is under 32 characters.
- `OPENAI_API_KEY` is optional and an empty key disables real OpenAI calls.
- `OPENAI_BASE_URL` is configurable; production should restrict this to trusted HTTPS endpoints.

API endpoints:

- API contracts were preserved for changed endpoints: same paths, methods, headers, and response shapes.
- The intentional behavior change is that notification endpoints now require bearer auth.
- Legacy `/feeds` remains readable without auth for public feed compatibility.

Input validation and sanitization:

- Numeric IDs are generally validated as positive in services.
- Page and limit parameters are clamped or validated.
- Search SQL uses prepared statement parameters; profile keyword search also has length and meta-character checks.
- Some free-text fields do not have tight length limits; add DTO validation annotations as a future hardening task.

SQL injection risks:

- JDBC calls use parameters for request data in the reviewed paths.
- Dynamic SQL predicates are built from constants or service-selected values. Interaction type concatenation is fed by hard-coded service calls or allowlisted switch statements.
- No request-controlled raw SQL interpolation was found in the reviewed backend code.

Command injection risks:

- The only command execution found is `ProcessBuilder` for `cwebp` with fixed executable/flags and server-generated file paths.
- No shell invocation or request-controlled command string was found.

Path traversal vulnerabilities:

- Upload storage uses a normalized base directory, sanitized directory names, UUID filenames, and a starts-with base path check before writing.
- Image URL resolution normalizes paths and checks readability under the configured upload base.
- No obvious path traversal issue was found in upload storage.

XSS and CSRF risks:

- The backend is JSON/API oriented and does not render user-controlled HTML templates.
- JWT bearer auth reduces classical cookie CSRF risk, assuming clients do not store bearer tokens in automatically attached cookies.
- If browser clients store bearer tokens in local storage, XSS prevention belongs primarily in the frontend; keep CORS restricted to trusted origins.

Dependency vulnerabilities:

- Embedded Tomcat was updated from 11.0.21 to 11.0.22 by Maven property override after Apache advisories were identified.
- Add automated dependency scanning in CI for continuous coverage across Spring, Jackson, MySQL Connector/J, springdoc, and transitive dependencies.

Docker and container security:

- Dockerfile uses a multi-stage build and final non-root `spring:spring` user.
- Compose binds backend only to `127.0.0.1:8080`, behind nginx.
- MySQL has no host port binding.
- Docker daemon is configured through `unix:///var/run/docker.sock`, not public TCP.
- Container capability and filesystem hardening remain future work.

CI/CD pipeline security:

- Tests run before image build/deploy.
- Docker Hub credentials and server SSH key are referenced through GitHub secrets.
- Recommended future work: pin action SHAs, minimize token permissions, deploy immutable image tags/digests, and avoid logging deploy output that could accidentally include sensitive runtime details.

File upload handling:

- Uploads are size-limited by Spring multipart settings: 10 MB per file and 30 MB request.
- Allowed image types are constrained and stored under generated names.
- JPEG/PNG content is decoded before storage; WebP/HEIC/HEIF rely on signature or brand checks.

Logging and sensitive data exposure:

- Application logs do not appear to print JWTs, database passwords, or OpenAI API keys.
- Coaching logs include plan/goal IDs, not full AI prompts or user health details.
- CI deploy logs tail backend logs; continue avoiding secrets in application log output.

Network and transport security:

- Public app traffic goes through nginx on 80/443. HTTP redirects to HTTPS.
- Backend is loopback-only at `127.0.0.1:8080`.
- MySQL is Docker-network only.
- HSTS is missing and nginx TLS protocol config should be narrowed to TLS 1.2/1.3.

Rate limiting and abuse prevention:

- Rate limiting is enabled by default.
- Auth, read, and write buckets are in-memory and IP-based.
- Coverage was expanded in this audit.
- Future work: distributed rate limiting if multiple backend instances are introduced, and stricter buckets for expensive AI/coaching endpoints.

Privilege escalation risks:

- Application container is non-root and not privileged.
- Host `ubuntu` user has high-privilege groups (`sudo`, `docker`, `lxd`). This is the main privilege escalation concern.

Third-party integrations:

- OpenAI client uses HTTPS by default and does not log the API key.
- Production should keep `OPENAI_BASE_URL` pinned to trusted endpoints and consider egress restrictions.

## Modified files

- `.gitignore`: added `backups/` to prevent committing database backups and sensitive dumps.
- `pom.xml`: added `tomcat.version` override to 11.0.22.
- `backups/gps_traces_before_watch_metrics_20260514_061332.sql`: removed tracked sensitive GPS backup from the working tree.
- `src/main/java/com/neostride/server/notification/controller/NotificationController.java`: required bearer auth and optional header consistency checks for notification APIs.
- `src/main/java/com/neostride/server/notification/service/NotificationService.java`: required user ID for single notification read/delete operations.
- `src/main/java/com/neostride/server/notification/repository/NotificationRepository.java`: scoped single notification read/delete SQL by `user_id` and `notification_id`.
- `src/main/java/com/neostride/server/community/repository/CommunityRepository.java`: scoped interaction cleanup to content owned by the caller.
- `src/main/java/com/neostride/server/config/RateLimitFilter.java`: expanded read/write rate-limit path coverage.
- `src/test/java/com/neostride/server/notification/NotificationControllerTest.java`: updated notification auth expectations.
- `src/test/java/com/neostride/server/community/repository/CommunityRepositoryTest.java`: added owner-scoped delete regression test.
- `src/test/java/com/neostride/server/config/RateLimitFilterTest.java`: added coverage test for legacy feed and notification paths.
- `docs/security-audit-2026-06-04.md`: this report.

## Hardening recommendations not implemented

- Purge the sensitive GPS backup from Git history, rotate any affected access paths if needed, and document incident-response handling for location data exposure.
- Disable or firewall `rpcbind.socket` unless an explicit RPC/NFS dependency exists.
- Add or verify a deny-by-default host/provider firewall policy. Allow 80/443 publicly, restrict SSH to trusted admin networks, and block port 111.
- Narrow nginx TLS protocols to TLS 1.2/1.3 only and add HSTS after confirming HTTPS coverage.
- Set explicit SSH hardening: `PermitRootLogin no`, `X11Forwarding no`, keep `PasswordAuthentication no`, and consider `AllowUsers` or source-IP restrictions.
- Reduce privileges of the deployment account. Remove unnecessary `docker` and `lxd` membership or split deployment into a constrained user/agent.
- Add compose hardening after testing: `read_only`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, explicit tmpfs, resource limits, and a dedicated uploads UID/GID.
- Pin GitHub Actions by commit SHA and deploy immutable image digests or Git SHA tags instead of `latest`.
- Add automated dependency vulnerability scanning, for example OWASP Dependency-Check, OSV Scanner, Dependabot, or GitHub dependency review.
- Add DTO validation annotations for maximum text lengths and stricter request shape validation where free-form input is accepted.
- Review HEIC/HEIF policy: fully decode/re-encode with vetted tooling or disable until server-side validation is equivalent to JPEG/PNG.
- Configure production `CORS_ALLOWED_ORIGINS` as an explicit trusted origin list. Avoid wildcard origins when credentials or bearer tokens are involved.
- Patch pending host packages (`apparmor`, `libapparmor1`, `cloud-init`) through the normal update process.

## Final assessment

The backend security posture is improved and reasonable for a small production API after this audit. The core application now consistently requires authenticated identity for notification operations, protects object-level mutations against IDOR/BOLA patterns, applies rate limiting to more abuse-prone endpoints, and uses a patched embedded Tomcat version. Existing positive controls include non-root application container execution, loopback-only backend exposure, no public MySQL binding, environment-based secrets, startup enforcement for JWT secret strength, prepared SQL statements in reviewed paths, and robust image upload path controls.

The main remaining risks are operational: the sensitive GPS backup must be purged from Git history, public `rpcbind` should be removed or firewalled, nginx/SSH should be tightened, the deployment user is highly privileged, and container/CI hardening can be improved. Addressing those items would move the project from a functional production posture to a stronger defense-in-depth posture.
