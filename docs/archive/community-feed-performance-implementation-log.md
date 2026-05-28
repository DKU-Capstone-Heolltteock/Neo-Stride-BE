# 커뮤니티 피드 성능 개선 적용 로그

작성일: 2026-05-26

## 1차 적용 범위

사용자에게 기존 기능 차이가 보이지 않도록 기존 `GET /api/community/feeds` 응답 구조와 endpoint는 유지했다. Android 클라이언트가 이미 가지고 있던 `liked`, `bookmarked`, `commented`, `tagged` 필드는 백엔드 목록 응답에 추가했다.

적용 내용:

- `ImageUrlResolver` 조회 시점 JPEG/PNG `ImageIO.read()` 제거
- 피드 목록 SQL의 row별 `TAG`/`LIKE`/`COMMENT` count dependent subquery 제거
- interaction count를 aggregate derived table로 조회
- 로그인 사용자용 목록 응답에 `liked`, `bookmarked`, `commented`, `tagged` projection 추가
- 공개 피드 조건을 `feed_scope <> 'PRIVATE'`에서 `feed_scope IN ('PUBLIC', 'FRIENDS')`로 변경
- Hikari connection pool 기본값 명시
- 피드 조회 관련 복합 인덱스 migration 추가 및 실제 MySQL 적용
- MySQL runtime `innodb_buffer_pool_size=2G`, `slow_query_log=ON`, `long_query_time=0.5` 적용
- MySQL compose에 buffer pool/slow query 설정 반영
- backend compose의 uploads bind mount를 기존 운영 경로 `/home/yoonhyeon/neo-stride/uploads:/data/uploads`로 고정

## 적용 전 기준치

측정 대상: `GET http://localhost:8080/api/community/feeds`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| before run1 | 0.229921s | 0.229762s | 1023B |
| before run2 | 0.233052s | 0.232819s | 1023B |
| before run3 | 0.240935s | 0.240886s | 1023B |

평균 total: 약 0.2346s

적용 전 SQL 실행계획 요약:

- `community_contents`: `ALL`
- Extra: `Using where; Using filesort`
- `community_interactions`: `DEPENDENT SUBQUERY` 3개
- MySQL `innodb_buffer_pool_size`: 128MB
- `slow_query_log`: OFF

## 적용 후 기준치

측정 대상: `GET http://localhost:8080/api/community/feeds`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| after run1 | 0.020126s | 0.019695s | 1155B |
| after run2 | 0.006465s | 0.006187s | 1155B |
| after run3 | 0.006214s | 0.005905s | 1155B |

평균 total: 약 0.0109s

첫 요청을 제외한 warm 상태 평균: 약 0.0063s

적용 후 SQL 실행계획 요약:

- `community_contents`: `range`, key `idx_cc_feed_list`
- `community_interactions`: derived aggregate, key `idx_ci_content_type`, `Using index`
- row별 dependent subquery 제거
- MySQL `innodb_buffer_pool_size`: 2GB
- `slow_query_log`: ON
- global `long_query_time`: 0.5s

## 비교

| 항목 | 적용 전 | 적용 후 |
| --- | ---: | ---: |
| 피드 API 평균 total | 약 0.2346s | 약 0.0109s |
| warm 상태 total | 약 0.23-0.24s | 약 0.006s |
| `community_contents` access | ALL scan | range/index 사용 |
| interaction count | dependent subquery 3개 | aggregate join |
| 이미지 URL 검증 | 조회 중 JPEG/PNG 디코딩 | 파일 존재/크기 확인만 |
| MySQL buffer pool | 128MB | 2GB |
| slow query log | OFF | ON |

현재 DB 데이터가 작기 때문에 절대 수치보다 실행계획 변화가 더 중요하다. 이번 변경은 게시글과 interaction 수가 증가할수록 효과가 커지는 유형이다.

## 주의 및 남은 작업

이후 단계에서 cursor pagination endpoint, Caddy 직접 서빙, JPEG thumbnail sidecar, community_content_stats, community_content_images, HTTP gzip compression, WebP opt-in thumbnail까지 백엔드에 적용했다. Android page API 전환은 사용자 지시에 따라 롤백했고, 현재 Android는 기존 `/api/community/feeds`와 상태 API 3개를 그대로 사용한다. 기능 차이를 만들 수 있는 목록 필드 제거는 적용하지 않고 gzip으로 대체했다.

## 롤백

DB 인덱스 롤백:

```sql
DROP INDEX idx_rel_user2_status_user1 ON relationships;
DROP INDEX idx_rel_user1_status_user2 ON relationships;
DROP INDEX idx_ci_tagged_type_content ON community_interactions;
DROP INDEX idx_ci_user_type_content ON community_interactions;
DROP INDEX idx_ci_content_type ON community_interactions;
DROP INDEX idx_cc_author_type_created ON community_contents;
DROP INDEX idx_cc_feed_list ON community_contents;
```

코드 롤백은 현재 git diff의 Java/application.properties 변경을 되돌리면 된다. DB 적용 전 백업은 `/tmp/neostride-perf-backup/neostride-before-indexes.sql`에 생성했다.


## 2차 적용 범위: cursor pagination

기존 `GET /api/community/feeds`는 그대로 유지하고, 신규 endpoint를 추가했다.

- `GET /api/community/feeds/page?limit=20`
- 다음 페이지 cursor: `cursorCreatedAt`, `cursorId`
- snake_case alias도 허용: `cursor_created_at`, `cursor_id`
- 응답: `{ items, nextCursor, hasMore }`
- 내부 쿼리는 `ORDER BY cc.created_at DESC, cc.content_id DESC LIMIT ?` 기반 keyset pagination 사용
- 기존 목록 endpoint의 응답 계약은 유지

배포 후 확인:

- `/api/community/feeds/page?limit=1` 첫 응답: feed `76`, `hasMore=true`, `nextCursor={createdAt=2026-05-26T22:14:32, feedId=76}`
- 해당 cursor로 다음 요청 시 feed `72`, `hasMore=false`

측정 대상: `GET http://localhost:8080/api/community/feeds/page?limit=1`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| page run1 | 0.006146s | 0.005828s | 621B |
| page run2 | 0.005481s | 0.005197s | 621B |
| page run3 | 0.007250s | 0.006917s | 621B |

같은 배포에서 기존 endpoint 재측정:

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| legacy run1 | 0.009953s | 0.009492s | 1155B |
| legacy run2 | 0.005934s | 0.005642s | 1155B |
| legacy run3 | 0.005878s | 0.005596s | 1155B |

`limit=20`은 현재 데이터 2건 기준 `0.007060s`, `1199B`였다. 데이터가 적어 절대 시간 차이는 작지만, 새 endpoint는 피드 수가 늘어나도 첫 요청에서 전체 피드를 가져오지 않는다.

## 3차 적용 범위: Caddy uploads 직접 서빙

Spring Boot resource handler를 유지하되, Caddy가 `/uploads/*`를 먼저 처리하도록 배포 설정을 바꿨다.

- Caddy read-only mount: `/home/yoonhyeon/neo-stride/uploads:/srv/neo-stride/uploads:ro`
- `/uploads/*`: Caddy `file_server`
- `Cache-Control: public, max-age=604800, immutable`
- 나머지 API 요청: 기존처럼 `127.0.0.1:8080` reverse proxy

주의: `yuni2.iptime.org` bare site block은 Caddy 자동 HTTPS를 시도했지만, `iptime.org` CAA 정책 때문에 Let's Encrypt 발급이 실패했다. 그래서 현재 Caddyfile은 검증 가능한 `http://yuni2.iptime.org` site block으로 전환했다. HTTPS를 유지하려면 별도 소유 도메인 또는 발급 가능한 인증서 전략이 필요하다.

측정 대상 파일: `/uploads/community/642f9eec-ba50-4cb5-9109-a5b0cfaca2e5.jpg` (`2,088,064B`)

Spring Boot 직접 서빙 기준:

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| spring image run1 | 0.015088s | 0.004955s | 2088064B |
| spring image run2 | 0.008868s | 0.002452s | 2088064B |
| spring image run3 | 0.008434s | 0.002674s | 2088064B |

Caddy 직접 서빙 기준:

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| caddy image run1 | 0.001211s | 0.000481s | 2088064B |
| caddy image run2 | 0.001080s | 0.000391s | 2088064B |
| caddy image run3 | 0.001124s | 0.000390s | 2088064B |

헤더 확인:

- `Server: Caddy`
- `Cache-Control: public, max-age=604800, immutable`
- API 요청은 `Via: 1.1 Caddy`로 Spring Boot에 reverse proxy됨

## 4차 적용 범위: JPEG thumbnail sidecar

원본 업로드 경로와 DB 저장값은 유지했다. 사용자가 기존 기능 차이를 느끼지 않도록 상세/업로드 응답은 원본 URL을 유지하고, 목록 계열 GET 응답에서만 sidecar 썸네일이 존재하면 썸네일 URL을 우선 내려준다. sidecar가 없으면 원본으로 fallback한다.

적용 내용:

- JPEG/PNG 업로드 시 `/uploads/{directory}/_thumbs/{uuid}_480.jpg` 생성
- Java ImageIO가 지원하지 않는 HEIC/HEIF/WebP는 원본 저장만 유지
- 기존 업로드 파일 백필 실행
- 목록 계열 GET에서만 썸네일 우선 선택
  - `/feeds`
  - `/api/community/feeds`
  - `/api/community/feeds/page`
  - `/api/community/search/feeds`
  - `/community/contents/*`
- 상세 응답은 원본 URL 유지

기존 파일 백필 결과:

| 항목 | 값 |
| --- | ---: |
| 변환 성공 | 86 files |
| 변환 실패 | 2 PNG files |
| 변환 대상 원본 총합 | 52,369,305B |
| 생성된 썸네일 총합 | 1,566,387B |

백필 실패 2건은 Java ImageIO의 PNG metadata 처리 실패이며, 해당 파일은 원본 URL fallback 대상이다.

대표 피드 이미지 비교:

| 항목 | size |
| --- | ---: |
| 원본 `/uploads/community/642f9eec...jpg` | 2,088,064B |
| 썸네일 `/uploads/community/_thumbs/642f9eec..._480.jpg` | 37,377B |

목록 응답은 이제 해당 피드 이미지에 대해 `_thumbs/*_480.jpg` URL을 반환한다.

측정 대상: `GET http://localhost:8080/api/community/feeds`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| legacy+thumbs run1 | 0.007095s | 0.006872s | 1215B |
| legacy+thumbs run2 | 0.006431s | 0.006152s | 1215B |
| legacy+thumbs run3 | 0.006130s | 0.005752s | 1215B |

측정 대상: `GET http://localhost:8080/api/community/feeds/page?limit=1`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| page+thumbs run1 | 0.029437s | 0.029079s | 645B |
| page+thumbs run2 | 0.005578s | 0.005276s | 645B |
| page+thumbs run3 | 0.005705s | 0.005457s | 645B |

측정 대상: Caddy 직접 썸네일 서빙

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| caddy thumb run1 | 0.000548s | 0.000516s | 37377B |
| caddy thumb run2 | 0.000485s | 0.000429s | 37377B |
| caddy thumb run3 | 0.000497s | 0.000448s | 37377B |

## 검증

테스트:

```bash
docker run --rm -v /home/yoonhyeon/projects/Neo-Stride/Neo-Stride-BE:/work -w /work maven:3.9.9-eclipse-temurin-21 mvn -q test
```

결과: 통과. Mockito/ByteBuddy의 JDK agent 경고만 출력됐다.

배포 후 확인:

- backend health: `200`
- backend uploads mount: `/home/yoonhyeon/neo-stride/uploads:/data/uploads`
- Caddy container: running
- Caddy `/uploads/*`: `200 OK`, `Server: Caddy`, cache header 포함
- 기존 `/api/community/feeds`: 정상 응답
- 신규 `/api/community/feeds/page`: cursor 정상 동작

## 현재까지의 핵심 비교

| 항목 | 적용 전 | 현재 |
| --- | ---: | ---: |
| 피드 API 평균 total | 약 0.2346s | warm 약 0.006s |
| 피드 API SQL | table scan + dependent subquery | index range + aggregate join |
| 이미지 URL 검증 | 조회 중 JPEG/PNG 디코딩 | 파일 존재/크기 확인 |
| 원본 이미지 서빙 | Spring Boot | Caddy file_server |
| 대표 원본 이미지 전송 | 2,088,064B | 상세/원본 요청 시 유지 |
| 대표 목록 이미지 전송 | 2,088,064B | 37,377B thumbnail |
| MySQL buffer pool | 128MB | 2GB |
| slow query log | OFF | ON |

## 추가 롤백

Caddy 직접 서빙 롤백:

1. `/home/yoonhyeon/projects/Neo-Stride/deploy/caddy/docker-compose.yml`에서 uploads mount 제거
2. `/home/yoonhyeon/projects/Neo-Stride/deploy/caddy/Caddyfile`에서 `handle_path /uploads/*` 블록 제거
3. `docker-compose -f /home/yoonhyeon/projects/Neo-Stride/deploy/caddy/docker-compose.yml up -d --force-recreate`

썸네일 응답 롤백:

- `ImageUrlResponseAdvice`의 목록 GET 썸네일 선택 로직을 제거하면 기존 원본 URL 응답으로 돌아간다.
- 생성된 `_thumbs` 디렉터리는 원본과 분리되어 있어 즉시 삭제하지 않아도 기능에는 영향이 없다.


## 5차 적용 범위: community_content_stats

피드/팁 count 조회가 `community_interactions`를 매번 집계하지 않도록 `community_content_stats`를 추가했다. 이 변경은 additive migration이며 기존 API 응답 필드와 값은 유지한다.

적용 내용:

- `community_content_stats` 테이블 추가
  - `content_id` PK/FK
  - `tagged_count`, `like_count`, `comment_count`, `bookmark_count`
- 기존 `community_interactions` 기준 backfill
- `community_interactions` INSERT/DELETE/UPDATE trigger로 stats 자동 유지
- 피드 목록/페이지/검색/상세 count 조회를 stats table join으로 변경
- 팁 목록/검색/상세 count 조회도 stats table join으로 변경

마이그레이션 적용 후 검증:

| 항목 | 값 |
| --- | ---: |
| stats rows | 6 |
| tagged sum | 0 |
| like sum | 6 |
| comment sum | 4 |
| bookmark sum | 3 |

동일 시점 `community_interactions` 집계도 `LIKE=6`, `COMMENT=4`, `BOOKMARK=3`으로 일치했다.

Trigger 검증은 transaction 안에서 수행했다.

| 단계 | content_id=76 like_count |
| --- | ---: |
| before | 1 |
| after INSERT LIKE | 2 |
| after ROLLBACK | 1 |

실행계획 비교:

| 항목 | stats 적용 전 | stats 적용 후 |
| --- | --- | --- |
| count source | derived aggregate over `community_interactions` | `community_content_stats` |
| `community_interactions` access | DERIVED, index scan, rows 12 | 피드 count 조회에서는 접근 없음 |
| stats join | `<derived2>` ref | `community_content_stats` PRIMARY `eq_ref` |

측정 대상: `GET http://localhost:8080/api/community/feeds`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| stats legacy run1 | 0.008222s | 0.007866s | 1215B |
| stats legacy run2 | 0.006752s | 0.006471s | 1215B |
| stats legacy run3 | 0.007109s | 0.006748s | 1215B |

측정 대상: `GET http://localhost:8080/api/community/feeds/page?limit=1`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| stats page run1 | 0.053936s | 0.053522s | 645B |
| stats page run2 | 0.006066s | 0.005745s | 645B |
| stats page run3 | 0.006006s | 0.005704s | 645B |

현재 데이터가 매우 작아 API wall time은 큰 차이가 나지 않는다. 이 변경의 핵심은 interaction 수가 커질 때 목록 조회가 interaction 전체 집계를 반복하지 않도록 만든 점이다.

추가 롤백:

```bash
docker exec -i neostride-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < deploy/mysql/migrations/007_community_content_stats.down.sql
```

코드 롤백은 repository의 stats table join을 이전 aggregate/subquery 방식으로 되돌리면 된다.


## 6차 적용 범위: community_content_images

기존 `community_contents.image` delimiter 컬럼은 유지하면서, 새 `community_content_images` 테이블을 병행 도입했다. 사용자에게 보이는 응답 구조는 그대로 유지하고, 새 테이블에 값이 없으면 legacy image 컬럼으로 fallback한다.

적용 내용:

- `community_content_images` 테이블 추가
  - `content_id`, `image_order`, `image_url`
  - `UNIQUE(content_id, image_order)`
  - `content_id` FK `ON DELETE CASCADE`
- 기존 `community_contents.image` delimiter 값을 backfill
- feed/tip insert/update 시 legacy column과 normalized table을 함께 갱신
- feed/tip 목록/페이지/검색/상세 조회에서 normalized table을 우선 사용
- normalized table 값이 없으면 기존 `community_contents.image`로 fallback

백필 결과:

| 항목 | 값 |
| --- | ---: |
| `community_contents.image` non-empty contents | 4 |
| `community_content_images` rows | 4 |
| distinct contents in image table | 4 |

백필된 row:

| content_id | image_order | image_url |
| ---: | ---: | --- |
| 25 | 0 | `/uploads/community/b2dd0f55-1f65-425a-8fd0-c642b2eab304.png` |
| 72 | 0 | `/uploads/community/642f9eec-ba50-4cb5-9109-a5b0cfaca2e5.jpg` |
| 75 | 0 | `/uploads/community/a329ea99-9630-40a0-b91d-3633a082ca7f.jpg` |
| 79 | 0 | `/uploads/community/3597272e-c6b6-426e-898c-0525a74bd8b1.jpg` |

실행계획 요약:

- `community_contents`: `range`, key `idx_cc_feed_list`
- `community_content_images`: derived aggregate, key `uq_cci_content_order`, rows 4
- primary query joins image aggregate by `content_id`

측정 대상: `GET http://localhost:8080/api/community/feeds`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| images table legacy run1 | 0.007392s | 0.007117s | 1215B |
| images table legacy run2 | 0.007882s | 0.007494s | 1215B |
| images table legacy run3 | 0.006473s | 0.006181s | 1215B |

측정 대상: `GET http://localhost:8080/api/community/feeds/page?limit=1`

| 구분 | total | start transfer | size |
| --- | ---: | ---: | ---: |
| images table page run1 | 0.051933s | 0.051576s | 645B |
| images table page run2 | 0.006470s | 0.006103s | 645B |
| images table page run3 | 0.006265s | 0.005965s | 645B |

현재 데이터에서는 성능 향상보다 데이터 구조 개선 성격이 크다. 직접적인 사용자 응답은 기존과 동일하고, 향후 이미지별 metadata, thumbnail/WebP URL, 삭제/정합성 검사를 테이블 단위로 처리할 기반이 생겼다.

WebP 확인:

- 현재 Java ImageIO runtime은 WebP writer를 제공하지 않는다.
- `ImageIO.getImageWritersByFormatName("webp").hasNext()` 결과: `false`
- runtime image에도 `cwebp`, `convert`, `magick` 같은 변환 도구가 없다.

이 시점에서는 WebP를 즉시 적용하지 않고 JPEG thumbnail sidecar를 유지했다. 이후 9차에서 Docker image에 `cwebp`를 포함하고, Android page API 호출에 opt-in header를 붙여 지원 클라이언트에만 WebP thumbnail URL을 내려주는 방식으로 적용했다.

추가 롤백:

```bash
docker exec -i neostride-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < deploy/mysql/migrations/008_community_content_images.down.sql
```

코드 롤백 전 DB 테이블을 먼저 제거하면 새 조회 SQL이 실패하므로, 롤백 시에는 백엔드 코드를 먼저 이전 버전으로 돌린 뒤 DB down migration을 적용한다.


## 7차 적용 범위: Android feed page API 전환 - 롤백됨

이 단계에서는 Android 피드 화면을 신규 cursor page API로 전환했으나, 이후 사용자 지시에 따라 Android 변경을 모두 롤백했다. 백엔드의 신규 `/api/community/feeds/page` endpoint는 유지하지만 현재 Android 앱은 기존 전체 목록 API와 상태 보정 API 3개를 그대로 호출한다.

적용 내용:

- 백엔드 `GET /api/community/feeds/page` endpoint는 유지
- Android `FeedApi.getFeedPage()`, `FeedPageResponse`, `FeedCursorResponse`, `FeedRepository.getFeedPage()`, `FeedFragment` page 전환 변경은 롤백
- 현재 Android 피드 화면은 `getFeedList()`와 `getBookmarkedFeeds`, `getLikedFeeds`, `getCommentedFeeds`를 계속 사용

호출 수 비교:

| 구분 | 피드 첫 화면 네트워크 호출 |
| --- | ---: |
| 적용 전 | 4개: `/api/community/feeds` + 북마크/좋아요/댓글 목록 |
| Android 롤백 후 현재 | 4개: `/api/community/feeds` + 북마크/좋아요/댓글 목록 |

서버 측 재측정:

| 대상 | total | size |
| --- | ---: | ---: |
| legacy `/api/community/feeds` | 0.006245s | 1215B |
| page `/api/community/feeds/page?limit=5` run1 | 0.006627s | 1259B |
| page `/api/community/feeds/page?limit=5` run2 | 0.005605s | 1259B |
| page `/api/community/feeds/page?limit=5` run3 | 0.005902s | 1259B |

현재 DB에는 피드가 2개뿐이라 page 응답 크기 절감은 아직 작다. 게시글 수가 늘면 기존 전체 목록 선조회가 `N`개를 모두 받는 반면 신규 API는 첫 화면 5개만 받으므로 네트워크와 JSON parsing 비용이 선형으로 줄어든다.

검증:

```bash
bash ./gradlew :app:compileDebugJavaWithJavac
```

결과: 당시 `BUILD SUCCESSFUL`. 이후 Android 변경은 롤백했고 롤백 후에도 `bash ./gradlew :app:compileDebugJavaWithJavac`가 `BUILD SUCCESSFUL`로 확인됐다.


## 8차 적용 범위: HTTP gzip compression

Android 카드에서 실제로 쓰는 목록 필드를 대조한 결과, 현재 `FeedUploadResponse`의 주요 필드는 피드 카드 렌더링 또는 상세 화면 intent 전달에 사용된다. `title`, `content`, count, 러닝 metric, image URL, route map, interaction state를 제거하면 사용자 화면이나 상세 진입 데이터가 달라질 수 있다. 따라서 필드 삭제 방식의 DTO 경량화는 보류하고, 응답 구조를 유지한 채 표준 HTTP gzip compression을 켰다.

적용 내용:

- `server.compression.enabled=true`
- JSON/text 계열 mime type 압축
- `server.compression.min-response-size=512`

적용 전후 비교:

| 대상 | 조건 | total | size | header |
| --- | --- | ---: | ---: | --- |
| `/api/community/feeds/page?limit=5` | 적용 전, `Accept-Encoding: gzip` | 0.006291s | 1259B | `Content-Encoding` 없음 |
| `/api/community/feeds/page?limit=5` | 적용 후, plain | 0.007410s | 1259B | 압축 없음 |
| `/api/community/feeds/page?limit=5` | 적용 후, gzip run1 | 0.009415s | 559B | `Content-Encoding: gzip` |
| `/api/community/feeds/page?limit=5` | 적용 후, gzip run2 | 0.011258s | 559B | `Content-Encoding: gzip` |

현재 샘플 데이터는 작아서 응답 시간 차이는 미미하지만, Android/HTTP 클라이언트가 gzip을 요청하면 JSON 전송량이 약 56% 줄어든다. 게시글 수와 텍스트 길이가 늘수록 효과가 커진다. Caddy를 통하지 않고 Spring Boot 8080에 직접 붙는 개발/앱 환경에서도 같은 이점이 적용된다.

검증:

```bash
docker run --rm -v $(pwd):/work -w /work maven:3.9.9-eclipse-temurin-21 mvn -q test
curl -H "Accept-Encoding: gzip" -D /tmp/feed-page-headers-gzip.txt http://localhost:8080/api/community/feeds/page?limit=5
```

결과:

- Backend test: 통과
- Runtime health: `200`
- Header: `Content-Encoding: gzip`
- Body size: `1259B -> 559B`


## 9차 적용 범위: WebP opt-in thumbnail

WebP는 모든 클라이언트에 강제로 적용하지 않고, 명시적으로 지원을 선언한 클라이언트에만 목록 썸네일 URL을 `.webp`로 내려주도록 적용했다. 기존 클라이언트와 상세 API는 기존 JPEG thumbnail 또는 original URL fallback을 유지한다.

적용 내용:

- Docker image에 `cwebp` 추가
  - 최종 이미지에 `apt install webp` 전체를 남기지 않고, build stage에서 `cwebp`와 직접 동적 라이브러리만 복사
  - 이미지 크기: 기존 배포 기준 466MB, 최종 WebP 지원 이미지 479MB
- 업로드 시 JPEG thumbnail 생성 후 WebP sidecar 생성
  - `*_480.jpg` 유지
  - `*_480.webp` 추가
  - `cwebp`가 없는 개발/테스트 환경에서는 WebP 생성만 skip하고 업로드는 계속 성공
- 기존 thumbnail backfill
  - JPEG thumbnail 86개 대상
  - WebP 86개 생성
  - 실패 0개
- 목록 계열 GET 응답에서 opt-in 시 WebP thumbnail URL 반환
  - header: `X-Neostride-Image-Format: webp`
  - query fallback: `imageFormat=webp` 또는 `image_format=webp`
- WebP opt-in은 header/query를 지원하지만, Android 변경은 롤백되어 현재 앱은 이 header를 보내지 않는다
- opt-in이 없으면 기존 `.jpg` thumbnail URL 유지

백필 결과:

| 항목 | 값 |
| --- | ---: |
| JPEG thumbnail count | 86 |
| WebP thumbnail count | 86 |
| JPEG thumbnail total bytes | 1,566,387B |
| WebP thumbnail total bytes | 961,708B |
| 전체 thumbnail 절감 | 약 38.6% |

대표 커뮤니티 이미지 비교:

| 파일 | size |
| --- | ---: |
| `642f9eec..._480.jpg` | 37,377B |
| `642f9eec..._480.webp` | 27,566B |

API 응답 동작 확인:

| 요청 | image URL |
| --- | --- |
| `/api/community/feeds/page?limit=5` | `*_480.jpg` |
| same endpoint + `X-Neostride-Image-Format: webp` | `*_480.webp` |

성능/전송량 측정:

| 대상 | total | size |
| --- | ---: | ---: |
| page API + gzip + JPEG URL warm | 0.007608s | 559B |
| page API + gzip + WebP URL warm run1 | 0.009288s | 559B |
| page API + gzip + WebP URL warm run2 | 0.006927s | 559B |
| Caddy JPEG thumbnail | 0.000480s | 37,377B |
| Caddy WebP thumbnail | 0.000458s | 27,566B |

JSON 응답 크기는 URL 확장자만 달라져 거의 동일하다. 실제 개선 효과는 이미지 다운로드에서 발생한다. 대표 이미지 기준으로 thumbnail 1장 전송량이 37,377B에서 27,566B로 줄었다.

검증:

```bash
docker run --rm -v $(pwd):/work -w /work maven:3.9.9-eclipse-temurin-21 mvn -q test
bash ./gradlew :app:compileDebugJavaWithJavac
docker exec neo-stride-backend sh -lc 'cwebp -version'
```

결과:

- Backend test: 통과
- Android rollback 후 Java compile: `BUILD SUCCESSFUL`
- Runtime health: `200`
- Runtime `cwebp`: `1.3.2`
- `git diff --check`: 통과


## Android 롤백 검증

사용자 지시에 따라 Android 저장소 변경은 모두 롤백했다.

확인 결과:

- Android repo status: `main...origin/main` clean
- `FeedApi`는 기존 `GET api/community/feeds` `getFeedList()`만 사용
- `feeds/page`, `X-Neostride-Image-Format`, `FeedPageResponse`, `FeedCursorResponse`, `getFeedPage` 흔적 없음
- Android compile: `bash ./gradlew :app:compileDebugJavaWithJavac` 성공

현재 Android가 호출하는 기존 API 검증:

검증용 임시 계정으로 로그인해 `Authorization` 토큰을 붙인 상태로 확인했고, 검증 후 `DELETE /users/me`는 `204`로 정리했다.

| API | 결과 | 비고 |
| --- | ---: | --- |
| `GET /api/community/feeds` + auth + `X-User-Id` | `200` | 기존 피드 목록 응답 OK. WebP opt-in header가 없으므로 JPG/PNG URL 응답 |
| `GET /community/contents/bookmarks` + auth | `200` | 기존 Android 상태 API OK |
| `GET /community/contents/likes` + auth | `200` | 기존 Android 상태 API OK |
| `GET /community/contents/comments` + auth | `200` | 기존 Android 상태 API OK |
| `GET /api/community/feeds/72` + auth + `X-User-Id` | `200` | 기존 상세 API OK. JPG/PNG URL 응답 |
| `GET /api/community/feeds/72/tagged-users` | `200` | 빈 배열 응답 OK |

참고로 상태 API 3종은 무토큰 호출 시 모두 `401`이었다. Android `ApiClient`가 로그인 토큰을 자동으로 붙이는 경로라 정상 동작이다.
