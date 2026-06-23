# Neo-Stride Backend

Spring Boot backend for the Neo-Stride capstone running app. The API currently covers authentication, running records with GPS traces, AI-assisted coaching plans and feedback, and community profile/feed/friend/tip features.

##  Storage

| Division | Link |
|------|------|
|  Android (Frontend) | [Neo-Stride](https://github.com/DKU-Capstone-Heolltteock/Neo-Stride) |
|  Spring Boot (Backend) | [Neo-Stride-BE](https://github.com/DKU-Capstone-Heolltteock/Neo-Stride-BE) |

## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Spring Web MVC, JDBC, Actuator
- MySQL 8.x
- springdoc-openapi Swagger UI
- Maven Wrapper
- Docker multi-stage build

## Local Setup

1. Install JDK 21 and Docker if you plan to build the container image.
2. Start or provision a MySQL 8.x database.
3. Create the `neostride` database and an application user.
4. Export the required environment variables listed below.
5. Run tests, then start the app with the Maven wrapper.

Example MySQL bootstrap:

```sql
CREATE DATABASE neostride CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'neostride_app'@'%' IDENTIFIED BY 'change-me';
GRANT SELECT, INSERT, UPDATE, DELETE ON neostride.* TO 'neostride_app'@'%';
FLUSH PRIVILEGES;
```

For Docker-hosted MySQL, prefer the provisioning helper so the backend runtime
does not use the MySQL root account:

```bash
MYSQL_CONTAINER=neostride-mysql USE_CONTAINER_MYSQL_ENV=true \
  APP_DB_PASSWORD=change-me \
  deploy/mysql/provision-app-user.sh
```

## Environment Variables

Required:

- `DB_USERNAME`: MySQL username.
- `DB_PASSWORD`: MySQL password.
- `JWT_SECRET`: HMAC signing secret for access and refresh tokens. Use at least 32 characters.

The app also imports secret files from `SECRETS_DIR` as a Spring config tree. By default this is `/run/secrets/`, so files named `DB_PASSWORD`, `JWT_SECRET`, or `OPENAI_API_KEY` can replace environment variables in production deployments that support secret mounts.

Optional:

- `DB_HOST`: MySQL host, default `localhost`.
- `DB_PORT`: MySQL port, default `3306`.
- `DB_NAME`: MySQL database name, default `neostride`.
- `JWT_ACCESS_TOKEN_TTL_SECONDS`: access token TTL, default `3600`.
- `JWT_REFRESH_TOKEN_TTL_SECONDS`: refresh token TTL, default `1209600`; a successful `/api/auth/refresh` issues a new refresh token with a fresh TTL window.
- `JWT_REFRESH_TOKEN_REPLAY_GRACE_SECONDS`: optional grace window for duplicate refresh requests after token rotation, default `0`.
- `SECRETS_DIR`: config tree directory for secret files, default `/run/secrets`.
- `CORS_ALLOWED_ORIGINS`: comma-separated allowed browser origins. Blank disables explicit CORS mappings.
- `CORS_ALLOWED_METHODS`: comma-separated allowed CORS methods, default `GET,POST,PUT,PATCH,DELETE,OPTIONS`.
- `RATE_LIMIT_ENABLED`: enables in-process API rate limits, default `true`.
- `RATE_LIMIT_AUTH_PER_MINUTE`: per-IP limit for auth endpoints, default `30`.
- `RATE_LIMIT_WRITE_PER_MINUTE`: per-IP limit for write endpoints, default `120`.
- `RATE_LIMIT_READ_PER_MINUTE`: per-IP limit for feed/search/tip reads, default `600`.
- `COMMUNITY_LEGACY_FEED_LIMIT`: internal cap for legacy feed list responses, default `100`.
- `COMMUNITY_DETAIL_COMMENT_LIMIT`: embedded comment cap for feed/tip detail responses, default `50`.
- `OPENAI_API_KEY`: enables real AI coaching plan/feedback generation. If unset, deterministic fallback logic is used.
- `OPENAI_BASE_URL`: OpenAI-compatible API base URL, default `https://api.openai.com/v1`.
- `OPENAI_MODEL`: chat model for coaching, default `gpt-5.4-mini`.
- `UPLOAD_READABLE_CACHE_TTL_MS`: stored upload existence cache TTL for response URL rewriting, default `5000`. Set `0` to disable.
- `UPLOAD_READABLE_CACHE_MAX_SIZE`: max stored upload existence cache entries, default `4096`.
- `IMAGE_THUMBNAIL_CORE_POOL_SIZE`: background thumbnail worker core size, default `1`.
- `IMAGE_THUMBNAIL_MAX_POOL_SIZE`: background thumbnail worker max size, default `2`.
- `IMAGE_THUMBNAIL_QUEUE_CAPACITY`: background thumbnail queue capacity, default `32`.

## MySQL Configuration

The application uses:

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:neostride}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
```

Apply the team's baseline schema before starting the app. The latest schema-only baseline is `deploy/mysql/schema/latest.sql`; it contains no data rows and reflects schema migrations through `028`.

For a fresh empty database, import the baseline and mark the included migrations as applied:

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$MIGRATION_DB_USERNAME" -p"$MIGRATION_DB_PASSWORD" "$DB_NAME" < deploy/mysql/schema/latest.sql
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh --baseline
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh --verify
```

Use an admin or migration DB user, not the runtime app user, to track and apply new `*.up.sql` files:

```bash
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh --status
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh
DB_USERNAME="$MIGRATION_DB_USERNAME" DB_PASSWORD="$MIGRATION_DB_PASSWORD" deploy/mysql/apply-migrations.sh --verify
```

For an existing database where the migration files were already applied manually, initialize only the tracking table once:

```bash
MYSQL_CONTAINER=neostride-mysql USE_CONTAINER_MYSQL_ENV=true deploy/mysql/apply-migrations.sh --baseline
```

The runner stores applied versions and SHA-256 checksums in `schema_migrations` and refuses to continue if an applied migration file changes. Use `--verify` after applying migrations to run matching `*.verify.sql` consistency checks. Verification SQL must return no rows; any returned row is treated as a failed check.

## Run

```bash
export DB_USERNAME=neostride_app
export DB_PASSWORD=change-me
export JWT_SECRET=replace-with-at-least-32-characters
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` by default. To run with production defaults, set `SPRING_PROFILES_ACTIVE=prod`; this disables Swagger/OpenAPI unless explicitly re-enabled.

## Test

```bash
./mvnw test
```

If the host machine does not have JDK 21, use the cached Maven/JDK Docker image:

```bash
docker run --rm -u "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" \
  -v "$HOME/.m2:/tmp/.m2" \
  -w /workspace \
  -e HOME=/tmp \
  -e MAVEN_CONFIG=/tmp/.m2 \
  -e MAVEN_OPTS=-Duser.home=/tmp \
  maven:3.9.11-eclipse-temurin-21 \
  ./mvnw test
```

## Swagger / OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health endpoint: `http://localhost:8080/actuator/health`
- In `prod`, Swagger/OpenAPI defaults to disabled. Override `SPRINGDOC_API_DOCS_ENABLED=true` and `SPRINGDOC_SWAGGER_UI_ENABLED=true` only for controlled review environments.

## Deployment Notes

- Build the container with `docker build -t neo-stride-backend .`.
- Provide required secrets through secret mounts or tightly controlled runtime environment variables, never committed property files.
- Prefer Docker/Kubernetes secret files mounted at `/run/secrets` for `DB_PASSWORD`, `JWT_SECRET`, and `OPENAI_API_KEY`; set `SECRETS_DIR` if another path is used.
- Run the backend with a least-privilege DB user such as `neostride_app`; keep MySQL root for database administration and migrations only.
- Run database migrations before deploying a new image, then run `deploy/mysql/apply-migrations.sh --verify`.
- Run production with `SPRING_PROFILES_ACTIVE=prod` unless Swagger/OpenAPI must be exposed for a controlled review.
- Verify the deployed app with `/actuator/health` after startup.
