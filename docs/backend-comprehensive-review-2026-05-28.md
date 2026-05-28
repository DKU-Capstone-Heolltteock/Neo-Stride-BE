# Spring Boot Backend Comprehensive Review

Review date: 2026-05-28
Target: Neo-Stride Spring Boot backend, Docker MySQL, self-hosted single-server deployment

## 1. 전체 평가 요약

현재 백엔드는 Spring MVC + JdbcTemplate 기반의 단일 모듈 서비스다. JPA Entity 기반 구조는 아니므로 Lazy/Eager 설정이나 ORM N+1보다는 SQL 작성 방식, transaction 경계, index, response 후처리, 이미지 서빙 구조가 실제 병목이다.

장점:

- 도메인이 `auth`, `running`, `coaching`, `community`, `notification`, `storage`로 크게 분리되어 있다.
- Android/iOS/Web 호환을 위해 기존 endpoint와 response record를 유지하려는 방향은 좋다.
- feed count는 `community_content_stats`와 trigger로 이미 비정규 count subquery에서 벗어났다.
- `/api/community/feeds/page` cursor pagination이 추가되어 있고 기존 `/api/community/feeds`도 유지되어 API contract 충돌이 낮다.
- Caddy가 `/uploads/*`를 직접 서빙하도록 운영 배포가 구성되어 있다.
- MySQL slow query log, 2GB buffer pool, Hikari 기본값이 이미 적용되어 있다.

가장 큰 리스크:

- 운영 backend가 DB root 계정을 사용하고, Docker inspect로 DB/OpenAI/JWT secret이 노출된다. 이미 노출된 secret은 회전해야 한다.
- 기존 상세 API는 API contract 유지를 위해 댓글 전체를 계속 포함하므로, 인기 게시물에서 payload가 커질 수 있다. 신규 comments cursor endpoint로 client 전환이 필요하다.
- `schema_migrations` 기반 apply script, `--verify`, 핵심 검증 SQL, 최신 schema-only baseline snapshot을 추가했다. 남은 리스크는 오래된 migration 검증 범위 확대다.
- thumbnail 생성은 `imageThumbnailExecutor`로 request thread에서 분리됐다. 남은 이미지 리스크는 기존 파일 backfill/metadata 운영과 실패 모니터링이다.
- search API는 `%LIKE%`와 offset pagination 중심이라 데이터 증가 시 별도 index/fulltext 전략이 필요하다.

운영 가능성 평가:

현재 데이터 규모에서는 운영 가능하다. 1차로 privacy predicate, reaction uniqueness, transaction 경계, running GPS N+1, feed/comment cursor 진입점, coaching feedback 호환성, upload thumbnail background processing을 개선했다. 다음 병목은 legacy detail payload, search indexing, baseline schema snapshot 갱신이다. 단일 홈서버에서는 Kubernetes/MSA보다 index, pagination, thumbnail, Caddy static serving, backup/migration 절차 정리가 우선이다.

## 2. 현재까지 적용한 변경

Coaching/running feedback:

- [src/main/java/com/neostride/server/coaching/dto/FeedbackRequest.java](../src/main/java/com/neostride/server/coaching/dto/FeedbackRequest.java)
  - 기존 `actual_pace_sec_per_km`는 유지하고, client가 보내는 `actual_pace_min_per_km`도 optional alias로 수용한다.
  - alias 값은 서버 내부에서 seconds/km로 반올림 변환한다.
- [src/main/java/com/neostride/server/coaching/service/CoachingService.java](../src/main/java/com/neostride/server/coaching/service/CoachingService.java)
  - feedback 요청 검증과 AI/fallback feedback 생성에 정규화된 pace 값을 사용한다.
  - `POST /api/coaching/plans/{plan_day_id}/feedback`는 비동기 큐가 아니라 요청 안에서 동기 처리하며, OpenAI 실패 시 fallback 문구를 DB에 저장한다.
- [src/main/java/com/neostride/server/coaching/repository/CoachingRepository.java](../src/main/java/com/neostride/server/coaching/repository/CoachingRepository.java)
  - goal이 완료 직후 비활성화되어도 오늘 완료된 plan/feedback은 `GET /api/coaching/plans/today`에서 다시 조회될 수 있도록 했다.
- [src/main/java/com/neostride/server/coaching/ai/OpenAiCoachingClient.java](../src/main/java/com/neostride/server/coaching/ai/OpenAiCoachingClient.java)
  - OpenAI feedback 호출 실패를 plan_day_id와 함께 warn log로 남긴다.

File/Image:

- [src/main/java/com/neostride/server/storage/StorageService.java](../src/main/java/com/neostride/server/storage/StorageService.java)
  - JPEG 업로드 시 EXIF orientation을 읽고, 필요한 경우 픽셀을 실제로 회전/반전한 JPEG로 저장한다.
  - 프로필 이미지와 썸네일이 원격 URL에서도 같은 방향으로 보이도록 저장 단계에서 normalize한다.

Community API/성능:

- [src/main/java/com/neostride/server/community/controller/CommunityController.java](../src/main/java/com/neostride/server/community/controller/CommunityController.java)
  - 기존 `GET /api/community/feeds` URL과 list response shape는 유지하면서 optional `limit`, `cursorCreatedAt`, `cursorId`를 추가했다.
  - 신규 `GET /api/community/feeds/{feedId}/comments`와 `GET /api/community/tips/{tipId}/comments` cursor endpoint를 추가했다.
  - `GET /api/friends/{userId}`를 추가해 남의 친구 목록을 볼 때 status가 요청자(JWT) 기준으로 계산되도록 했다.
- [src/main/java/com/neostride/server/community/service/CommunityService.java](../src/main/java/com/neostride/server/community/service/CommunityService.java)
  - legacy feed list는 파라미터가 없으면 기존 전체 목록 흐름을 유지하고, cursor 파라미터가 있으면 내부적으로 page query를 사용한다.
  - 댓글 cursor 응답은 `limit + 1` 조회로 `hasMore`와 `nextCursor`를 계산한다.
- [src/main/java/com/neostride/server/community/repository/CommunityRepository.java](../src/main/java/com/neostride/server/community/repository/CommunityRepository.java)
  - feed/tip query에서 전체 `community_content_images`를 매번 `GROUP BY`하던 derived table을 제거했다.
  - image URL 집계는 `content_id` 기준 indexed lookup으로 바꿔 page limit이 있는 query에서 불필요한 전체 image table aggregation을 피한다.
  - comments page query는 `created_at ASC, interaction_id ASC` cursor와 limit을 사용하고, content visibility를 먼저 검증한다.
  - 남의 친구 목록 status는 목록 소유자와의 관계가 아니라 요청자와 각 목록 항목 간 관계(`none/sent/received/friends/blocked`)로 계산한다.

이전 1차 수정:

- FRIENDS scope visibility predicate를 실제 accepted relationship 기준으로 수정했다.
- unauthenticated 요청에서는 `X-User-Id`를 신뢰하지 않도록 optional viewer context를 정리했다.
- Community write method에 transaction 경계를 추가했다.
- interaction uniqueness migration과 idempotent toggle 흐름을 추가했다.
- coaching running record 409 원인으로 확인된 `plans.feedback VARCHAR(100)` 제한을 `TEXT`로 확장했다.
- [src/main/java/com/neostride/server/running/repository/RunningRecordRepository.java](../src/main/java/com/neostride/server/running/repository/RunningRecordRepository.java)
  - 러닝 기록 목록/월별/상세 조회에서 GPS trace를 row mapper 내부에서 매 행마다 조회하던 DB-level N+1 구조를 batch 조회로 변경했다.
  - 월별 조회 조건을 `YEAR(created_at)`/`MONTH(created_at)`에서 range predicate로 변경했다.
- [deploy/mysql/migrations/013_query_pattern_indexes.up.sql](../deploy/mysql/migrations/013_query_pattern_indexes.up.sql), [deploy/mysql/migrations/014_community_interaction_uniqueness.up.sql](../deploy/mysql/migrations/014_community_interaction_uniqueness.up.sql), [deploy/mysql/migrations/015_widen_plan_feedback.up.sql](../deploy/mysql/migrations/015_widen_plan_feedback.up.sql)
  - query pattern index, reaction uniqueness, coaching feedback 길이 확장을 운영 DB에 적용했다.

운영 DB 확인:

- `plan_id=2922`, `user_id=1000004`: `plans.feedback IS NULL`, `is_completed=0`, 연결된 `running_records` 없음. 즉 해당 plan_id에는 성공한 feedback 저장/record 연결이 없었다.
- 최신 실기기 저장으로 보이는 `plan_id=3034`: `is_completed=1`, feedback 저장 완료, `run_record_id=116` 연결 확인.
- 운영 로그: OpenAI plan generation timeout이 확인됐다. feedback generation은 기존 코드가 실패를 조용히 fallback 처리했으므로 이번 변경부터 warn log가 남는다.

운영 DB 적용:

- 적용 전 backup:
  - `/tmp/neostride-before-013-query-pattern-indexes.sql`
  - `/tmp/neostride-before-014-community-interaction-uniqueness.sql`
  - `/tmp/neostride-before-015-widen-plan-feedback.sql`
  - `/tmp/neostride-before-agent-test-data-cleanup.sql`
- backend health 확인: `200`
- 테스트/에이전트 생성 의심 user tuple 21개 삭제 및 cascade count 0 확인. 실사용으로 보이는 `1000004`, `1000011`, `1000029`, `1000038`은 유지했다.

검증:

- `./mvnw test`는 현재 호스트가 JRE라서 compiler 없음으로 실패한다.
- Docker JDK 환경 전체 테스트를 사용한다.

## 3. 심각도별 이슈 목록

### Critical

#### C1. FRIENDS scope 피드가 친구 검증 없이 노출됨

- 상태: 해결 완료.

- 위치: `CommunityRepository.listFeeds`, `listFeedsPage`, `searchFeeds` 계열. `cc.feed_scope IN ('PUBLIC', 'FRIENDS')` 조건 사용.
- 문제: 비로그인 또는 친구가 아닌 사용자도 `FRIENDS` 피드를 목록에서 볼 수 있다.
- 원인: block 관계만 제외하고 accepted friendship 조건을 적용하지 않는다.
- 영향도: privacy/data exposure. 실제 서비스에서는 가장 먼저 고쳐야 한다.
- 해결방안: 기존 endpoint/JSON은 유지하고 SQL predicate만 변경한다.
  - 비로그인: `feed_scope = 'PUBLIC'`
  - 로그인: `PUBLIC OR author_user_id = viewer OR (FRIENDS AND accepted relationship exists)`
  - 기존 `/api/community/feeds` 응답 구조는 유지.
  - 회귀 테스트: 비친구는 FRIENDS feed 미노출, 친구/작성자는 노출.
- API 호환성 영향: URL/field/status 변경 없음. 단, 잘못 노출되던 데이터가 사라지는 보안 수정이다.

#### C2. 운영 secret 노출 및 DB root 계정 사용

- 위치: 운영 Docker env, backend compose, MySQL 접속 설정.
- 문제: backend container env에 DB password, JWT secret, OpenAI API key가 들어 있으며 `docker inspect`로 확인 가능하다. backend가 DB root로 접속한다.
- 원인: root 계정 재사용, container env 기반 secret 주입, secret rotation 절차 부재.
- 영향도: host 계정 또는 CI 로그 접근자가 DB/OpenAI/JWT를 탈취할 수 있다.
- 해결방안:
  - OpenAI key, JWT secret, DB password 즉시 rotation.
  - DB app user 생성: `neostride_app`에 `neostride.*` 최소 권한 부여.
  - backend `DB_USERNAME`을 root에서 app user로 변경.
  - `.env` 파일 권한 `600`, 배포 로그에서 env 출력 금지.
  - Docker secret까지는 과할 수 있으나, 최소한 root 계정 제거는 필수.
- API 호환성 영향: 없음.

### High

#### H1. Legacy feed list는 전체 데이터 반환 경로가 남아 있음

- 상태: 부분 해결.

- 위치: `GET /api/community/feeds`, `CommunityRepository.listFeeds`.
- 문제: 신규 page endpoint는 있지만 기존 feed endpoint는 limit 없이 전체 반환한다.
- 원인: API contract 유지를 위해 legacy endpoint를 유지했다.
- 영향도: 게시글 수 증가 시 JSON 크기, image URL 후처리, DB scan, client memory가 선형 증가.
- 해결방안:
  - 클라이언트는 `/api/community/feeds/page?limit=20&cursorCreatedAt=...&cursorId=...`로 전환.
  - legacy endpoint는 deprecated 문서화 후 optional `limit`를 받도록 확장.
  - 충분한 공지 후 default internal cap 적용을 검토.
- API 호환성 영향: 신규 endpoint/optional param 추가는 호환. legacy cap은 rollout 필요.

#### H2. interaction 중복 방지 constraint 부재

- 상태: 해결 완료.

- 위치: `community_interactions`, `toggleInteraction`, `toggleBookmark`.
- 문제: LIKE/BOOKMARK/TAG가 count-then-insert 방식이며 unique constraint가 없다.
- 원인: COMMENT와 reaction을 같은 테이블에 넣었고 interaction type별 uniqueness 모델이 없다.
- 영향도: 동시 요청에서 duplicate row, count drift, toggle 결과 불일치 가능.
- 해결방안:
  - 데이터 dedupe migration 먼저 실행.
  - LIKE/BOOKMARK는 `(user_id, content_id, interaction_type)` 중복 방지 구조 도입.
  - COMMENT는 여러 개 허용해야 하므로 별도 comments table 분리 또는 generated unique key 전략 필요.
  - TAG는 `(content_id, tagged_user_id, interaction_type)` unique 검토.
  - toggle은 transaction + insert-ignore/delete 기반 idempotent 처리.
- API 호환성 영향: 없음. 내부 schema/data migration 필요.

#### H3. Community write flow transaction 경계 부족

- 상태: 1차 해결 완료.

- 위치: `CommunityService.uploadFeed`, `uploadTip`, `updateFeed`, `createComment`, `toggleFeedLike` 등.
- 문제: content insert, image table insert, tag insert, notification insert가 부분 성공할 수 있다.
- 원인: 일부 write method에 `@Transactional`이 없다.
- 영향도: image row 누락, tag/notification 누락, count trigger 상태 불일치 가능.
- 해결방안: write method에 `@Transactional` 적용. notification은 같은 transaction으로 둘지 outbox/after-commit으로 둘지 결정. 현재 규모에서는 같은 transaction도 허용 가능.
- API 호환성 영향: 없음.

#### H4. feed image aggregation derived table이 전체 image table을 매번 group by

- 상태: 1차 해결 완료.

- 위치: feed/tip query의 `LEFT JOIN (SELECT content_id, GROUP_CONCAT(...) FROM community_content_images GROUP BY content_id)`.
- 문제: page limit이 있어도 derived table은 전체 image rows를 group by할 수 있다.
- 원인: 먼저 content id를 제한한 뒤 image를 batch 조회하지 않는다.
- 영향도: 이미지 row가 늘면 `temporary/filesort` 또는 derived table 비용 증가.
- 해결방안:
  - 1단계: content ids page 조회.
  - 2단계: `WHERE content_id IN (...) ORDER BY content_id, image_order`로 image batch 조회.
  - 또는 CTE로 limited contents 후 images join.
- API 호환성 영향: 없음.

#### H5. comments 전체 반환과 pagination 부재

- 상태: 부분 해결.

- 위치: `findFeedDetail`, `findTipDetail`, `commentsForContent`.
- 문제: 상세 조회가 댓글 전체를 항상 반환한다.
- 원인: comment list가 detail DTO에 직접 포함되어 있고 limit/cursor가 없다.
- 영향도: 인기 게시물 상세 응답이 커지고 comment sort 비용 증가.
- 해결방안:
  - 기존 detail 응답은 유지하되 신규 `GET /api/community/feeds/{id}/comments?limit&cursor` 추가.
  - detail에는 초기 N개만 optional로 내려주는 v2 endpoint를 별도 추가하거나 client 전환 후 deprecate.
  - 이미 적용한 `idx_ci_content_type_created`가 comments 정렬을 지원한다.
- API 호환성 영향: 신규 endpoint는 호환. 기존 detail 축소는 migration guide 필요.

#### H6. migration 운영 체계가 수동이고 baseline과 운영 schema가 벌어짐 - 해결 완료

- 위치: `deploy/mysql/apply-migrations.sh`, `deploy/mysql/migrations/*.sql`.
- 문제: 새 DB를 init schema만으로 만들면 stats/images/index/notification 등 최신 구조와 다르다.
- 원인: 기존에는 migration runner나 `schema_migrations` table이 없었다.
- 영향도: 재설치/복구/테스트 DB에서 운영과 다른 schema가 만들어진다.
- 적용: `schema_migrations` table을 생성하고, `*.up.sql` 파일을 version/checksum 기반으로 적용하는 runner를 추가했다. 운영 DB는 002-016 migration 적용/기록이 완료됐다.
- 적용: `--verify` 모드와 007/008/013/014/015/016 검증 SQL을 추가했다. 016은 신규 content 생성 시 stats 0-row를 보장하는 trigger와 누락 row backfill을 포함한다.
- 적용: [deploy/mysql/schema/latest.sql](../deploy/mysql/schema/latest.sql) schema-only baseline snapshot을 추가했고 임시 DB restore 후 `--baseline`/`--verify` 통과를 확인했다.
- 남은 작업: 오래된 migration 검증 SQL 추가 확대.
- API 호환성 영향: 없음.

### Medium

#### M1. Image thumbnail 생성이 upload request thread에서 수행됨 - 해결 완료

- 위치: `StorageService.storeImage`, `AsyncConfig.imageThumbnailExecutor`.
- 문제: JPEG resize와 cwebp 실행이 업로드 요청 thread에서 동기 수행됐다.
- 원인: background image executor가 없었다.
- 영향도: 큰 이미지나 cwebp 지연 시 upload latency와 servlet thread 점유 증가.
- 적용: 원본 저장/검증 후 `imageThumbnailExecutor`에 thumbnail/WebP 생성을 예약한다. executor는 기본 core 1, max 2, queue 32로 bounded 처리한다.
- 남은 운영 포인트: queue reject 로그 모니터링, 기존 이미지 backfill/metadata 관리.
- API 호환성 영향: 없음. 목록 응답은 thumbnail 존재 시 사용하고 없으면 원본 fallback을 유지한다.

#### M2. 조회 시 image URL마다 filesystem stat 수행 - 해결 완료

- 위치: `ImageUrlResolver.isReadableStoredUpload`.
- 문제: `Files.isRegularFile`/`Files.size`를 응답 URL마다 수행한다.
- 원인: 응답 직전 파일 존재성 검증을 유지한다.
- 영향도: 대량 list 응답에서 filesystem I/O 증가. 기존 `ImageIO.read` 제거로 위험은 많이 줄었다.
- 적용: `ImageUrlResolver`에 short TTL memory cache를 추가해 같은 stored upload URL의 `Files.isRegularFile`/`Files.size` 반복 호출을 줄였다. 기본 TTL은 5초, max entry는 4096이며 환경변수로 조정 가능하다.
- API 호환성 영향: 없음.

#### M3. 검색 API가 `%LIKE%`와 offset pagination에 의존

- 위치: `searchFeeds`, `searchTips`, `searchProfiles`.
- 문제: `LOWER(content_text) LIKE '%keyword%'`는 index 사용이 어렵다.
- 원인: 검색용 normalized column/fulltext index가 없다.
- 영향도: 콘텐츠 증가 시 full scan과 offset 비용 증가.
- 해결방안: MySQL FULLTEXT index 또는 별도 normalized search column 도입. offset deep page는 cursor 전환.
- API 호환성 영향: optional 신규 endpoint/param이면 호환.

#### M4. refresh token이 stateless라 폐기/탈취 대응이 약함

- 위치: `JwtTokenService`, `AuthService.refresh`.
- 문제: refresh token을 DB에 저장하지 않아 logout, device revocation, reuse detection이 어렵다.
- 원인: 단순 JWT refresh 구조.
- 영향도: token 탈취 시 TTL 동안 계속 재발급 가능.
- 해결방안: `refresh_tokens` table에 hashed token id, user_id, expires_at, revoked_at 저장. refresh마다 rotation하고 이전 토큰 폐기.
- API 호환성 영향: request/response 유지 가능.

#### M5. CORS/rate limit 정책이 명시적이지 않음

- 위치: Spring config/Caddy config.
- 문제: Web client 운영 시 CORS가 환경별로 불명확하고, login/upload/feed에 rate limit이 없다.
- 원인: Spring Security/filter 기반 공통 정책 부재.
- 영향도: 브라우저 연동 문제, brute force/upload abuse 가능.
- 해결방안: 허용 origin을 env로 관리하는 CORS config 추가. Caddy 또는 Spring interceptor에서 login/upload 기본 rate limit 추가.
- API 호환성 영향: 정상 client origin을 등록하면 없음.

#### M6. feed 목록 SQL에 `Using filesort`가 남음

- 위치: feed list/page EXPLAIN.
- 문제: `feed_scope IN ('PUBLIC','FRIENDS')` 때문에 `idx_cc_feed_list`를 쓰면서도 filesort가 남는다.
- 원인: IN 조건과 order by 조합.
- 영향도: 현재는 작지만 대량 feed에서 page query가 느려질 수 있다.
- 해결방안: privacy fix와 함께 `PUBLIC`/`FRIENDS` 조건을 분리해 union/merge하거나, visibility 모델을 재설계한다.
- API 호환성 영향: 없음.

### Low

#### L1. CommunityController와 CommunityRepository가 과도하게 큼

- 위치: `CommunityController`, `CommunityRepository`.
- 문제: multipart parsing, DTO 변환, notification, SQL, delimiter encoding이 한 파일에 몰려 있다.
- 원인: 빠른 기능 추가 중심 구조.
- 영향도: 변경 영향 범위 파악이 어렵고 테스트가 커진다.
- 해결방안: API contract를 유지한 채 내부만 `FeedService`, `TipService`, `RelationshipService`, `CommentRepository`, `FeedImageRepository` 등으로 분리.
- API 호환성 영향: 없음.

#### L2. delimiter 기반 `content_text`가 남아 있음

- 위치: `content_text`, `encodeFeedContent`, `decodeFeedContent`, tip content encode/decode.
- 문제: title/content/route/metrics/course address가 delimiter 문자열에 의존한다.
- 원인: schema 변경 없이 필드를 확장한 이력.
- 영향도: delimiter 충돌, 검색/정렬/검증 어려움.
- 해결방안: nullable columns 추가 후 backfill, dual-write, read fallback, legacy column deprecate.
- API 호환성 영향: 없음. DB migration 필요.

#### L3. Swagger/OpenAPI가 production에서도 켜져 있음

- 위치: `application.properties`, SpringDoc startup warning.
- 문제: public API schema가 외부에 노출될 수 있다.
- 원인: capstone/review 편의 설정 유지.
- 영향도: 보안상 직접 취약점은 아니지만 공격 표면 정보를 제공한다.
- 해결방안: prod profile에서 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` 또는 Caddy basic auth/IP 제한.
- API 호환성 영향: 없음.

## 4. 도메인별 리뷰

Auth:

- 자체 JWT HS256 구현은 secret 길이 검증과 constant-time compare가 있다.
- access/refresh `type` claim 분리는 좋다.
- refresh token 저장/폐기/회전 DB가 없어 보안 운영성은 부족하다.
- DB root 사용과 secret rotation 부재가 가장 큰 문제다.

Community:

- 가장 많은 기능과 리스크가 집중되어 있다.
- feed count/stats, privacy predicate, reaction uniqueness, write transaction, legacy feed optional cursor, comments cursor endpoint까지 1차 개선했다. 남은 핵심은 detail comments payload 축소 rollout, repository 책임 분리, 검색/이미지 후처리다.
- Repository가 notification 생성까지 수행해 책임이 크다.
- 목록 응답에 `liked/bookmarked/commented/tagged`를 포함하는 방향은 client API 호출 수 감소 측면에서 좋다.

Running:

- GPS trace row-by-row 조회는 이번 변경으로 batch 조회로 개선했다.
- 월별 조회의 `YEAR()/MONTH()` predicate도 date range로 바꿨다.
- 추가 index `idx_rr_user_created`, `idx_gps_record_time` 적용 완료.

Coaching:

- create/feedback flow는 transaction이 잡혀 있다.
- slow log에 `goals` update lock wait가 반복 기록되었고, 이번에 `idx_goals_user_active_created`, `idx_plans_*` 인덱스를 적용했다.
- OpenAI call은 plan refresh에서는 async/after commit이라 적절하다. feedback은 request path에서 동기 호출되므로 timeout/fallback을 계속 유지해야 한다.

Notification:

- 단순 DB insert 구조라 현재 규모에는 충분하다.
- interaction/relationship transaction과 묶이는 지점은 정리 필요. 장기적으로 outbox가 안정적이지만 지금은 과한 구조일 수 있다.

File/Image:

- 업로드 시 magic byte 검증, path normalization, thumbnail 생성이 있다.
- 조회 시 ImageIO decode는 제거되어 좋다.
- Caddy direct serving과 cache header가 적용되어 있다.
- thumbnail generation은 `imageThumbnailExecutor`로 request thread에서 분리됐다.

Operations:

- MySQL slow log ON, `long_query_time=0.5`, buffer pool 2GB 확인.
- backend/mysql/caddy compose가 서로 다른 디렉토리에 있어 운영 drift가 생길 수 있다.
- DB backup과 migration 적용 절차를 문서화/스크립트화해야 한다.

## 5. 성능 개선 제안

이미 적용/확인됨:

- `idx_cc_feed_list`, `idx_ci_content_type`, `idx_ci_user_type_content`, relationship 양방향 index.
- `community_content_stats` count table과 trigger.
- `community_content_images` normalized side table.
- Caddy `/uploads/*` direct serving + `Cache-Control`.
- MySQL `innodb_buffer_pool_size=2G`, slow query log ON.
- Hikari pool 설정 명시.
- `013_query_pattern_indexes` 운영 적용.

다음 개선:

1. Android/iOS/Web feed client를 optional cursor 또는 `/api/community/feeds/page`로 전환.
2. Android/iOS/Web comments client를 신규 cursor endpoint로 전환.
3. client 전환 후 detail comments payload 축소 또는 v2 detail endpoint 도입.
4. 이미지 row가 더 늘면 feed/tip image 조회를 content page 조회 + image batch 조회로 한 번 더 분리.
5. search fulltext/index 전략 도입.
6. upload thumbnail background executor 도입.
7. Redis는 위 항목 이후 first page/hot feed cache만 검토.

## 6. DB schema 개선 계획

1. Reaction/comment 분리 또는 interaction uniqueness 강화.
2. `content_text` delimiter 제거:
   - `title`, `body`, `route_map_image_url`, `distance`, `duration`, `pace`, `course_address` nullable columns 추가.
   - 기존 delimiter 값 backfill.
   - dual-read fallback 유지.
   - dual-write 검증 후 legacy field deprecate.
3. `community_content_images`는 이미 도입됨. image type, width, height, mime, byte_size, thumbnail status 컬럼을 추가하면 운영성이 좋아진다.
4. `schema_migrations` table 도입.

## 7. Migration 및 rollback 전략

공통 원칙:

- migration 전 `mysqldump`와 uploads directory snapshot을 만든다.
- row count, FK count, stats count 검증 SQL을 migration마다 같이 둔다.
- backward-compatible nullable column 추가 -> backfill -> dual-write -> read switch -> cleanup 순서로 진행한다.

이번 013 rollback:

```sql
DROP INDEX idx_plans_user_date ON plans;
DROP INDEX idx_plans_goal_date ON plans;
DROP INDEX idx_plans_user_goal_date ON plans;
DROP INDEX idx_goals_user_active_created ON goals;
DROP INDEX idx_gps_record_time ON gps_traces;
DROP INDEX idx_rr_user_created ON running_records;
DROP INDEX idx_ci_content_type_created ON community_interactions;
```

Stats 검증:

```sql
SELECT COUNT(*) AS missing_stats
FROM community_contents cc
LEFT JOIN community_content_stats s ON s.content_id = cc.content_id
WHERE s.content_id IS NULL;

SELECT SUM(
  stats.like_count <> actual.like_count OR
  stats.comment_count <> actual.comment_count OR
  stats.tagged_count <> actual.tagged_count OR
  stats.bookmark_count <> actual.bookmark_count
) AS mismatched_stats
FROM community_content_stats stats
JOIN (
  SELECT cc.content_id,
    COALESCE(SUM(ci.interaction_type='LIKE'),0) AS like_count,
    COALESCE(SUM(ci.interaction_type='COMMENT'),0) AS comment_count,
    COALESCE(SUM(ci.interaction_type='TAG'),0) AS tagged_count,
    COALESCE(SUM(ci.interaction_type='BOOKMARK'),0) AS bookmark_count
  FROM community_contents cc
  LEFT JOIN community_interactions ci ON ci.content_id = cc.content_id
  GROUP BY cc.content_id
) actual ON actual.content_id = stats.content_id;
```

Image 검증:

- DB URL 목록 추출.
- `/home/yoonhyeon/neo-stride/uploads`에서 원본 존재 확인.
- thumbnail은 없으면 원본 fallback 가능하므로 원본 무손실이 우선이다.

Downtime 최소화:

- index 추가는 현재 규모에서는 online에 가깝게 적용 가능.
- 큰 테이블이 되면 off-peak에 실행하고, MySQL 8 online DDL 가능 여부를 사전 확인한다.
- destructive migration은 하지 않는다. drop은 한 배포 이상 뒤로 미룬다.

## 8. 실제 작업 우선순위

지금 바로:

1. secret rotation + DB app user 전환.
2. Android/iOS/Web에서 feed cursor query 또는 `/api/community/feeds/page` 사용 시작.
3. Android/iOS/Web에서 comments cursor endpoint 사용 시작.
4. 남은 오래된 migration 검증 SQL/rollback rehearsal 확대.

다음 스프린트:

1. client 전환율 확인 후 legacy no-param feed cap 정책 결정.
2. detail comments 축소/v2 endpoint migration guide 작성.
3. feed/tip image 조회 batch 분리 추가 검토.
4. refresh token persistence/rotation.
5. search fulltext/index 전략 도입.

장기:

1. delimiter 기반 `content_text` 제거.
2. community repository 분리.
3. optional Redis first-page cache.
4. notification outbox 검토.

## 9. 최종 산출물 요약

병목 원인:

- legacy full feed/detail payload, image original/thumbnail 처리, search `%LIKE%`/offset pagination, migration drift, secret/root DB 운영, 일부 장기 구조 부채.

적용 가능한 개선안:

- 적용 완료: cursor pagination 진입점, additional composite indexes, stats table, Caddy static serving, batch GPS trace loading, date range monthly query, comments pagination endpoint, transaction/uniqueness fixes, coaching feedback request 호환, background thumbnail executor, migration apply script/schema_migrations, 운영 baseline 기록, schema-only baseline snapshot. 다음 적용: search index/fulltext.

API 호환성 영향:

- 대부분 endpoint/JSON 유지 가능.
- 신규 endpoint/optional field/optional query param 중심으로 개선한다.
- legacy response 축소나 comments 분리는 deprecation + migration guide 필요.

필요 migration:

- 이미 적용: 006-013 인덱스/stats/images/notifications/pace normalization.
- 이미 적용: interaction dedupe/unique.
- 다음 필요: content_text column normalization, refresh_tokens.

예상 성능 개선 효과:

- feed warm local latency는 현재 약 수 ms 수준.
- 데이터 증가 시 cursor pagination, comments page 분리, Caddy thumbnail serving이 가장 큰 체감 개선이다.
- running list는 records N개일 때 GPS query N회에서 1회로 감소했다.
- coaching lock wait는 goals/plans index로 완화될 가능성이 높다.

남은 리스크:

- secret rotation, DB app user 전환, detail comments payload, search/index drift, 오래된 migration 검증 SQL 부족.

테스트 및 검증 계획:

- feed privacy repository/controller regression.
- interaction duplicate/race test.
- `deploy/mysql/apply-migrations.sh --verify`로 migration row count/stats consistency test.
- upload validation/path traversal tests 유지.
- running monthly range and GPS batch tests 유지.
- production slow log weekly review.

## Follow-up applied on 2026-05-28

- Fixed community feed visibility so unauthenticated feed/search only returns PUBLIC posts, while authenticated viewers can see PUBLIC, own, and accepted-friend FRIENDS posts. Detail and interaction paths now enforce the same visibility plus blocked-user filtering.
- Added interaction uniqueness migration for LIKE, BOOKMARK, and TAG while preserving multiple COMMENT rows.
- Widened `plans.feedback` from `VARCHAR(100)` to `TEXT` because coaching running save calls feedback completion in the same transaction; long AI/fallback feedback could raise `DataIntegrityViolationException` and roll back `POST /api/running/records` with HTTP 409.
- Added `actual_pace_min_per_km` as an optional feedback request alias while preserving `actual_pace_sec_per_km`. The server normalizes the alias to seconds/km before AI/fallback feedback generation.
- Adjusted today-plan lookup so a just-completed plan remains readable after the client marks the goal inactive/achieved.
- Added feed/comment cursor compatibility and removed the feed/tip image derived-table aggregation.
- Added `GET /api/friends/{userId}` with viewer-based relationship status so another user's friend list does not render every row as `friends` for the requester.
- Normalized JPEG EXIF orientation during image upload so portrait profile photos are stored with corrected pixels before serving over HTTP.
- Cleaned obvious test/agent DB users after taking `/tmp/neostride-before-agent-test-data-cleanup.sql`; real-looking users were left intact.
