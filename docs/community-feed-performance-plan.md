# 커뮤니티 피드 조회 성능 개선 계획

작성일: 2026-05-26

대상 환경은 단일 물리 홈서버에서 Docker 기반으로 운영되는 Spring Boot 백엔드, Docker MySQL 8.4, 로컬 업로드 이미지 저장소, Android 클라이언트 조합이다. Kubernetes, sharding, read replica 같은 운영 부담이 큰 구조보다 현재 서버의 남는 RAM과 단순한 배포 구조를 활용하는 방향을 우선한다.

## 1. 현재 병목 원인 분석

### API 흐름

커뮤니티 피드 목록 조회 흐름은 다음과 같다.

1. Android `FeedFragment`가 `FeedRepository.getFeedList()`를 호출한다.
2. `FeedApi`는 `GET /api/community/feeds`를 호출한다.
3. 백엔드 `CommunityController.getCommunityFeedList()`가 `CommunityService.getFeedList()`로 위임한다.
4. `CommunityRepository.listFeeds(viewerUserId)`가 `feedQuery()` 또는 `feedQueryForViewer()`를 실행한다.
5. 응답 직전 `ImageUrlResponseAdvice`가 이미지 URL을 public URL로 변환한다.

핵심 문제는 메인 피드 목록 API가 서버 페이지네이션 없이 전체 공개 피드를 조회한다는 점이다. 검색 API에는 `LIMIT ? OFFSET ?`가 있지만, 실제 피드 홈에서 호출하는 `/api/community/feeds`에는 `LIMIT`가 없다.

### SQL 구조

현재 목록 SQL은 다음 특징을 가진다.

- `community_contents cc`
- `users u` inner join
- `community_users cu` left join
- `running_records rr` left join
- 행마다 `community_interactions`에 대해 `TAG`, `LIKE`, `COMMENT` count 서브쿼리 3개 실행
- 로그인 사용자가 있으면 차단 관계 확인용 `relationships` `NOT EXISTS` 추가
- 정렬은 `ORDER BY cc.created_at DESC, cc.content_id DESC`

실제 `EXPLAIN` 결과는 현재 데이터가 작음에도 `community_contents`를 `ALL` scan하고 `Using where; Using filesort`를 사용한다. `community_interactions` count는 `DEPENDENT SUBQUERY`로 표시되며, 각 피드 행마다 반복된다.

### 인덱스 상태

실제 운영 DB에서 확인한 인덱스는 대부분 FK 단일 인덱스다.

- `community_contents`: `PRIMARY(content_id)`, `author_user_id`, `running_record_id`
- `community_interactions`: `PRIMARY(interaction_id)`, `user_id`, `content_id`, `tagged_user_id`
- `relationships`: `PRIMARY(relation_id)`, `UNIQUE(user1_id, user2_id)`, `user2_id`
- `running_records`: `PRIMARY(run_record_id)`, `user_id`, `plan_id`

현재 피드 목록에 필요한 `(content_type, feed_scope, created_at, content_id)` 복합 인덱스가 없고, interaction count 및 상태 확인에 필요한 `(content_id, interaction_type)`, `(user_id, interaction_type, content_id)` 복합 인덱스가 없다.

### N+1 또는 N+1 유사 문제

JPA ORM lazy loading 문제는 아니다. 이 백엔드는 `JdbcTemplate` 기반이다. 다만 SQL 내부에서 피드 행마다 count 서브쿼리 3개를 실행하므로, 애플리케이션 레벨 N+1은 아니지만 DB 레벨의 row-by-row dependent subquery 문제가 있다.

상세 조회는 `findFeedDetail()` 본문 조회 후 `commentsForContent()`를 별도 호출한다. 상세 1건 기준으로는 허용 가능하지만, 댓글이 매우 많은 글에는 댓글 pagination이 없다.

### 페이지네이션

- 메인 피드 목록: 서버 페이지네이션 없음
- 검색 피드/팁/프로필: offset pagination 사용
- Android 피드 화면: 서버에서 전체 피드를 받은 뒤 `PAGE_SIZE = 5`로 클라이언트에서 5개씩 표시

따라서 게시글이 늘어나면 첫 화면에서도 전체 피드 JSON, 전체 피드 이미지 URL 검증, 전체 count 계산이 한 번에 발생한다.

### 이미지 로딩 방식과 저장 위치

이미지는 백엔드 컨테이너의 `/data/uploads`에 저장되고, 실제 호스트 경로는 `/home/yoonhyeon/neo-stride/uploads`로 bind mount되어 있다. DB에는 `/uploads/community/...`, `/uploads/routes/...`, `/uploads/profile/...` 형태의 경로가 저장된다.

백엔드 `UploadWebConfig`가 Spring MVC resource handler로 `/uploads/**`를 직접 서빙한다. Caddy는 `gzip zstd` 후 모든 요청을 `127.0.0.1:8080`으로 reverse proxy한다. 즉 이미지 정적 파일도 Spring Boot를 거쳐 나간다.

현재 업로드 이미지는 원본 파일 저장 위주다. 확인된 community 이미지 중 다수가 약 1.4MB이고, 일부는 2.5MB 수준이다. 피드 카드에서 실제 표시되는 크기는 108dp 수준인데 원본 이미지를 내려준다.

### 응답 직전 이미지 파일 검증 병목

`ImageUrlResolver.toPublicUrl()`은 `/uploads/...` URL마다 실제 파일 존재 여부와 크기를 확인한다. JPEG/PNG는 `ImageIO.read(path.toFile())`로 디코딩 가능 여부까지 검사한다. 이 작업이 목록 응답의 모든 프로필 이미지, route 이미지, feed 이미지마다 수행된다.

현재 데이터가 2개 feed, 3개 tip으로 작아도 로컬 `/api/community/feeds` 응답은 약 0.10-0.25초가 관찰되었다. payload 자체는 약 1KB라서 DB보다 응답 후처리의 파일 I/O 및 이미지 디코딩 비용이 이미 보인다.

### API payload 크기

현재 운영 DB 샘플 기준 `/api/community/feeds`는 약 1KB였지만, 구조상 전체 피드를 반환하므로 피드 수와 함께 선형 증가한다. 목록 응답에는 본문 전체, route image URL, 이미지 URL 목록, count 등이 포함된다. 대규모 클라우드가 아니라 홈서버 업로드 회선 환경에서는 JSON보다 실제 이미지 원본 다운로드가 더 큰 병목이 된다.

### Docker 컨테이너 및 volume 구조

현재 실행 상태:

- `neo-stride-backend`: `snvnn/neo-stride-backend:latest`, healthy, host `8080 -> container 8080`
- `neostride-mysql`: `mysql:8.4`, healthy, host `3306 -> container 3306`
- 백엔드 메모리 제한: 없음
- MySQL 메모리 제한: 없음
- MySQL volume: Docker named volume `mysql_neostride_mysql_data:/var/lib/mysql`
- 업로드 이미지: host bind mount `/home/yoonhyeon/neo-stride/uploads:/data/uploads`
- Caddy: host network, `yuni2.iptime.org` reverse proxy to backend

### MySQL connection pool 상태

`application.properties`에는 Hikari pool 설정이 없다. Spring Boot 기본 Hikari 설정이면 maximum pool size는 보통 10이다. 실제 DB 상태에서는 `Threads_connected=12`, `Max_used_connections=14`, `max_connections=151`가 확인되었다. 현재는 pool 부족이라고 단정할 수 없지만, 피드 화면이 한 번에 4개 API를 병렬 호출하므로 사용자 수가 늘면 pool wait이 발생할 수 있다.

### CPU/RAM/I/O/네트워크 평가

현재 Docker stats 기준 백엔드와 MySQL은 각각 약 460-470MB 수준이며, 서버 전체 RAM 32GB 대비 매우 낮다. CPU도 idle에 가깝다. 즉 현재 사양에서는 RAM이 병목이 아니라 MySQL 설정, 쿼리 구조, 이미지 원본 전송, Spring을 통한 정적 파일 서빙이 더 현실적인 병목이다.

홈 네트워크에서는 다운로드보다 업로드 대역폭이 제한되는 경우가 많다. 외부 사용자가 1.5MB 원본 이미지를 여러 장 로드하면 DB 쿼리가 빠르더라도 체감 로딩은 느려진다.

### 프론트엔드 렌더링 병목

Android는 RecyclerView와 Glide를 사용하므로 기본적인 lazy image loading은 된다. 그러나 서버에서 전체 피드를 먼저 받고, 별도로 좋아요/북마크/댓글 상태 API 3개를 호출한다. 또한 Glide는 피드 카드 108dp 이미지와 route thumbnail에도 원본 URL을 그대로 요청한다. 따라서 프론트 병목은 "렌더링 엔진"보다 "전체 데이터 선조회 + 원본 이미지 다운로드 + 중복 상태 API"가 핵심이다.

## 2. 가장 가능성 높은 병목 우선순위

1. 이미지 원본 전송과 정적 파일 서빙 구조
   - 108dp 카드에 1.4-2.5MB 원본 이미지를 전송한다.
   - Caddy가 캐시 없이 Spring Boot로 이미지를 reverse proxy한다.
   - 홈서버 업로드 대역폭이 외부 사용자 체감 속도를 좌우한다.

2. 서버 페이지네이션 부재
   - `/api/community/feeds`가 전체 피드를 반환한다.
   - Android의 `PAGE_SIZE=5`는 클라이언트 표시 단위일 뿐 네트워크/DB 부하는 줄이지 못한다.

3. interaction count/status SQL 구조
   - 목록 조회에서 `TAG`, `LIKE`, `COMMENT` count dependent subquery가 row마다 반복된다.
   - 로그인 사용자의 liked/bookmarked/commented 상태가 목록 응답에 통합되지 않아 Android가 상태 API 3개를 추가 호출한다.

4. 인덱스 부족
   - 피드 목록 정렬/필터 복합 인덱스가 없다.
   - interaction count/status용 복합 인덱스가 없다.
   - relationships 차단 확인용 양방향 조회 인덱스가 부족하다.

5. 응답 직전 이미지 디코딩 검증
   - 목록 응답마다 `ImageIO.read()`가 JPEG/PNG를 읽는다.
   - 이미지 유효성 검사는 업로드 시점에 끝내고 조회 시점에는 하지 않는 편이 맞다.

6. MySQL 기본 설정
   - `innodb_buffer_pool_size=134217728` 즉 128MB다.
   - 서버 RAM 여유가 큰데 DB 캐시가 지나치게 작다.
   - slow query log가 꺼져 있다.

7. API payload 과다
   - 게시글 수가 늘면 전체 본문과 이미지 URL 목록이 계속 커진다.
   - 목록 DTO와 상세 DTO 분리가 더 필요하다.

8. Docker volume 또는 디스크 I/O
   - 현재는 데이터가 작아 직접 증거는 약하다.
   - 이미지 파일 검증/전송과 MySQL volume이 같은 물리 디스크라면 동시 I/O가 체감 지연을 만들 수 있다.

9. CPU 동시 처리 한계
   - i7-7700 4C/8T는 현재 규모에서는 충분하다.
   - 다만 이미지 변환을 요청 동기 처리로 넣으면 CPU 병목이 될 수 있으므로 background 처리로 제한해야 한다.

10. 프론트 렌더링 자체
   - RecyclerView/Glide 사용으로 기본 구조는 나쁘지 않다.
   - 실제 문제는 서버/네트워크 요청량과 원본 이미지다.

## 3. 서버 사양 기준 리소스 평가

### RAM

32GB 중 available 약 24GB라면 MySQL buffer pool을 128MB로 둘 이유가 없다. 단일 MySQL + Spring Boot + Caddy + 기타 컨테이너 구성에서는 우선 2GB부터 시작하고, DB 데이터와 여유를 보며 4GB까지 올릴 수 있다. 개인 홈서버이므로 무리하게 16GB 이상을 할당할 필요는 없다.

권장 시작값:

```text
innodb_buffer_pool_size=2G
innodb_buffer_pool_instances=2
```

DB 데이터가 커지고 여전히 RAM 여유가 있으면:

```text
innodb_buffer_pool_size=4G
innodb_buffer_pool_instances=4
```

### MySQL container memory limit

현재 MySQL 컨테이너 메모리 제한은 없다. OOM 위험은 낮지만, 명시적으로 제한을 둔다면 buffer pool보다 충분히 크게 잡아야 한다. 예: buffer pool 2GB라면 컨테이너 limit 4GB 이상.

### Connection pool

현재 evidence만으로 pool 부족은 1순위가 아니다. 다만 앱 첫 화면이 feed + 상태 API 3개를 병렬 호출하므로 기본 pool 10은 사용자 수가 조금만 늘어도 병목이 될 수 있다.

권장:

```properties
spring.datasource.hikari.maximum-pool-size=15
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=3000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.idle-timeout=600000
```

i7-7700 단일 DB에서는 pool을 50 이상으로 키우는 방식은 피한다. 동시 쿼리만 늘려 CPU context switching과 DB lock 경합을 키울 수 있다.

### CPU

4C/8T는 피드 조회와 이미지 정적 서빙 정도에는 충분하다. 그러나 WebP 변환, 썸네일 생성, 이미지 리사이즈를 요청 thread에서 동기 처리하면 CPU spike가 생길 수 있다. 업로드 후 background executor에서 제한된 동시성으로 처리해야 한다.

권장:

- 이미지 처리 worker concurrency: 1-2
- 원본 저장 후 즉시 응답 또는 처리 상태 관리
- 조회 API에서는 변환 작업 금지

### Disk I/O

MySQL named volume과 업로드 이미지가 같은 물리 디스크에 있으면 이미지 전송과 DB random read가 겹친다. SSD면 당장 큰 문제는 아닐 수 있지만, HDD면 이미지 대량 전송 중 DB latency가 튈 수 있다.

권장:

- DB volume과 uploads가 실제 어느 디스크에 있는지 확인
- HDD라면 DB와 uploads를 SSD로 이전
- 최소한 slow query log와 `iostat`/`iotop`으로 근거 수집

### 네트워크

홈서버 외부 접속은 업로드 대역폭이 가장 쉽게 병목이 된다. 원본 이미지 1.5MB 10장을 한 화면에서 받으면 사용자 1명에게도 15MB 업로드가 발생한다. 20Mbps 업로드 환경이면 이론상 6초 이상이 걸릴 수 있다.

## 4. 단기 개선안

### 4.1 서버 cursor pagination 도입

`GET /api/community/feeds?limit=20&cursorCreatedAt=...&cursorId=...` 형태를 추가한다. 정렬 기준이 이미 `created_at DESC, content_id DESC`이므로 cursor 조건은 다음처럼 잡는다.

```sql
WHERE cc.content_type = 'POST'
  AND cc.feed_scope <> 'PRIVATE'
  AND (
    ? IS NULL
    OR cc.created_at < ?
    OR (cc.created_at = ? AND cc.content_id < ?)
  )
ORDER BY cc.created_at DESC, cc.content_id DESC
LIMIT ?
```

응답은 다음 형태가 좋다.

```json
{
  "items": [],
  "nextCursor": {
    "createdAt": "2026-05-26T22:14:32",
    "id": 76
  },
  "hasMore": true
}
```

기존 `/api/community/feeds`는 호환을 위해 유지하되 default `limit=20`을 적용하거나, 신규 `/api/community/feeds/page`를 먼저 추가해 Android를 이전한다.

### 4.2 복합 인덱스 추가

우선 추가할 인덱스:

```sql
CREATE INDEX idx_cc_feed_list
ON community_contents (content_type, feed_scope, created_at DESC, content_id DESC);

CREATE INDEX idx_cc_author_type_created
ON community_contents (author_user_id, content_type, created_at DESC, content_id DESC);

CREATE INDEX idx_ci_content_type
ON community_interactions (content_id, interaction_type);

CREATE INDEX idx_ci_user_type_content
ON community_interactions (user_id, interaction_type, content_id);

CREATE INDEX idx_ci_tagged_type_content
ON community_interactions (tagged_user_id, interaction_type, content_id);

CREATE INDEX idx_rel_user1_status_user2
ON relationships (user1_id, status, user2_id);

CREATE INDEX idx_rel_user2_status_user1
ON relationships (user2_id, status, user1_id);
```

`feed_scope <> 'PRIVATE'`는 인덱스 효율이 `=` 조건보다 떨어질 수 있다. 공개 피드가 대부분이면 `content_type, created_at, content_id` 인덱스도 검토한다. 실제 선택은 `EXPLAIN ANALYZE`로 확인한다.

### 4.3 count 서브쿼리 제거 또는 집계 join

목록 조회에서 count dependent subquery 3개를 다음 중 하나로 바꾼다.

단기:

```sql
LEFT JOIN (
  SELECT content_id,
         SUM(interaction_type='TAG') AS tagged_count,
         SUM(interaction_type='LIKE') AS like_count,
         SUM(interaction_type='COMMENT') AS comment_count
  FROM community_interactions
  GROUP BY content_id
) stats ON stats.content_id = cc.content_id
```

더 좋은 단기:

1. cursor로 먼저 `community_contents`에서 20개만 고른다.
2. 그 20개 content_id에 대해서만 interaction aggregate를 join한다.

### 4.4 목록 응답에 사용자 상태 포함

Android가 별도 호출하는 북마크/좋아요/댓글 상태 API 3개를 줄인다. 피드 목록 SQL에 현재 사용자 기준 상태를 넣는다.

```sql
EXISTS (
  SELECT 1 FROM community_interactions ci
  WHERE ci.content_id = cc.content_id
    AND ci.user_id = ?
    AND ci.interaction_type = 'LIKE'
) AS liked
```

동일하게 `bookmarked`, `commented`, `tagged`를 포함한다. 이렇게 하면 첫 화면 API가 4개에서 1개로 줄어든다.

### 4.5 조회 시점 이미지 디코딩 제거

`ImageUrlResolver`의 `ImageIO.read()` 검증은 업로드 시점 또는 별도 정합성 점검 배치에서 수행한다. 조회 시점에는 경로 정규화와 URL 변환만 수행한다.

단기 대안:

- 운영 profile에서는 `Files.isRegularFile`까지만 확인
- 더 나아가 목록 응답에서는 파일 존재 확인도 생략
- 깨진 파일 검증은 관리 스크립트로 수행

### 4.6 Spring Boot가 아닌 Caddy/Nginx로 이미지 직접 서빙

Caddy가 `/uploads/*`를 백엔드로 넘기지 말고 host path에서 직접 file server로 제공하게 한다.

예시:

```caddyfile
yuni2.iptime.org {
    encode gzip zstd

    handle_path /uploads/* {
        root * /srv/neo-stride/uploads
        file_server
        header Cache-Control "public, max-age=604800, immutable"
    }

    reverse_proxy 127.0.0.1:8080
}
```

이 경우 Caddy 컨테이너에 uploads를 read-only로 mount한다.

```yaml
volumes:
  - /home/yoonhyeon/neo-stride/uploads:/srv/neo-stride/uploads:ro
```

### 4.7 썸네일 URL 추가

피드 목록에는 원본 `imageUrls` 대신 `thumbnailUrls`를 내려준다. 현재 카드 크기는 108dp이므로 320px 또는 480px WebP면 충분하다.

권장 파일 구조:

```text
uploads/
  originals/community/{uuid}.jpg
  thumbs/community/{uuid}_320.webp
  thumbs/community/{uuid}_720.webp
  routes/{uuid}.webp
  profile/{uuid}_160.webp
```

단기에는 기존 원본 경로를 유지하고 새 썸네일만 추가 생성한다.

### 4.8 MySQL 튜닝 및 slow query log

MySQL command 또는 config file에 아래를 추가한다.

```text
--innodb-buffer-pool-size=2G
--innodb-buffer-pool-instances=2
--slow-query-log=ON
--long-query-time=0.5
--log-queries-not-using-indexes=ON
```

개인 홈서버에서는 `long_query_time=0.5`부터 시작하고, 로그가 너무 많으면 1초로 올린다.

## 5. 중장기 개선안

### 5.1 DB 정규화

현재 `community_contents.content_text`에 title/content/route/metrics를 delimiter로 합쳐 저장하고, `image`에 여러 이미지를 delimiter로 합쳐 저장한다. 조회 때마다 문자열 split이 필요하고, 이미지별 썸네일/원본 관리가 어렵다.

중장기 테이블:

```sql
CREATE TABLE community_content_images (
  image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  content_id BIGINT NOT NULL,
  image_order INT NOT NULL,
  original_url VARCHAR(500) NOT NULL,
  thumb_url VARCHAR(500) NULL,
  width INT NULL,
  height INT NULL,
  byte_size BIGINT NULL,
  mime_type VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cci_content
    FOREIGN KEY (content_id) REFERENCES community_contents(content_id)
    ON DELETE CASCADE,
  UNIQUE KEY uq_cci_content_order (content_id, image_order),
  KEY idx_cci_content (content_id)
);
```

그리고 `community_contents`에는 다음 컬럼을 추가한다.

```sql
ALTER TABLE community_contents
  ADD COLUMN title VARCHAR(200) NULL,
  ADD COLUMN body TEXT NULL,
  ADD COLUMN route_map_image_url VARCHAR(500) NULL,
  ADD COLUMN distance DECIMAL(8,2) NULL,
  ADD COLUMN duration_seconds INT NULL,
  ADD COLUMN pace_seconds INT NULL;
```

### 5.2 count denormalization

피드 목록의 count 계산 비용을 줄이기 위해 `community_content_stats`를 둔다.

```sql
CREATE TABLE community_content_stats (
  content_id BIGINT PRIMARY KEY,
  tagged_count INT NOT NULL DEFAULT 0,
  like_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  bookmark_count INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ccs_content
    FOREIGN KEY (content_id) REFERENCES community_contents(content_id)
    ON DELETE CASCADE
);
```

좋아요/댓글/태그 생성/삭제 시 application transaction 안에서 count를 증감한다. 홈서버 규모에서는 트리거보다 애플리케이션 코드에서 명시적으로 관리하는 편이 디버깅이 쉽다.

### 5.3 Redis 캐시

Redis는 1순위는 아니다. 먼저 pagination, index, thumbnail을 적용해야 한다. 이후에도 피드 첫 페이지가 자주 호출되면 Redis를 도입한다.

권장 캐시:

- `community:feed:first:{viewerGroup}` TTL 10-30초
- `content:stats:{contentId}` TTL 1-5분 또는 write-through
- 사용자별 liked/bookmarked set은 데이터가 작을 때만 TTL 캐시

개인 홈서버에서는 Redis AOF 영속화까지 강하게 요구하지 않아도 된다. 캐시는 재생성 가능 데이터이므로 RDB snapshot 또는 no persistence도 가능하다. 다만 Docker 재시작 후 warm-up 지연을 감수해야 한다.

### 5.4 object storage 분리

현 단계에서는 로컬 파일 + Caddy/Nginx 정적 서빙이 가장 단순하다. 외부 object storage는 운영 복잡도와 비용을 늘린다. 단, 홈 네트워크 업로드 대역폭이 계속 병목이면 Cloudflare R2, Backblaze B2, S3 호환 storage + CDN을 검토한다.

## 6. 데이터베이스 구조 개선안

우선순위는 다음과 같다.

1. 복합 인덱스 추가
2. cursor pagination용 repository/controller/service 추가
3. 목록 projection query 개선
4. 사용자 interaction 상태를 목록 응답에 포함
5. 이미지 테이블 분리
6. stats 테이블 도입
7. delimiter 기반 `content_text`, `image` 제거

권장 마이그레이션 순서:

### 6.1 인덱스 마이그레이션

```sql
CREATE INDEX idx_cc_feed_list
ON community_contents (content_type, feed_scope, created_at DESC, content_id DESC);

CREATE INDEX idx_ci_content_type
ON community_interactions (content_id, interaction_type);

CREATE INDEX idx_ci_user_type_content
ON community_interactions (user_id, interaction_type, content_id);
```

데이터가 많지 않으면 online DDL 부담은 작다. 그래도 운영 전 백업 후 적용한다.

### 6.2 이미지 테이블 backfill

기존 `community_contents.image` delimiter 문자열을 읽어서 `community_content_images`에 insert한다.

검증:

```sql
SELECT COUNT(*) FROM community_contents WHERE image IS NOT NULL AND image <> '';
SELECT COUNT(DISTINCT content_id) FROM community_content_images;
```

둘이 정확히 같을 필요는 없다. 기존 image 문자열이 여러 이미지를 포함하므로 다음 검증이 더 정확하다.

- 기존 delimiter split 결과 총 이미지 수
- 신규 `community_content_images` row count
- content_id별 image_order 연속성

### 6.3 stats 테이블 backfill

```sql
INSERT INTO community_content_stats (content_id, tagged_count, like_count, comment_count, bookmark_count)
SELECT cc.content_id,
       SUM(ci.interaction_type='TAG'),
       SUM(ci.interaction_type='LIKE'),
       SUM(ci.interaction_type='COMMENT'),
       SUM(ci.interaction_type='BOOKMARK')
FROM community_contents cc
LEFT JOIN community_interactions ci ON ci.content_id = cc.content_id
GROUP BY cc.content_id
ON DUPLICATE KEY UPDATE
  tagged_count = VALUES(tagged_count),
  like_count = VALUES(like_count),
  comment_count = VALUES(comment_count),
  bookmark_count = VALUES(bookmark_count);
```

## 7. Docker 및 데이터 마이그레이션 계획

데이터 무손실이 최우선이다. 단일 서버 환경이므로 복잡한 무중단 마이그레이션보다 검증 가능한 짧은 maintenance window를 권장한다.

### 7.1 사전 점검

```bash
docker ps
docker inspect neostride-mysql
docker inspect neo-stride-backend
```

확인할 항목:

- MySQL volume source: `/var/lib/docker/volumes/mysql_neostride_mysql_data/_data`
- upload source: `/home/yoonhyeon/neo-stride/uploads`
- backend env `NEOSTRIDE_UPLOAD_BASE_DIR=/data/uploads`
- Caddy가 uploads를 직접 서빙한다면 read-only mount 경로 일치 여부

### 7.2 DB 논리 백업

maintenance 시작 직전:

```bash
BACKUP_DIR=/home/yoonhyeon/neo-stride/backups/$(date +%Y%m%d_%H%M%S)
mkdir -p "$BACKUP_DIR"

docker exec neostride-mysql sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --set-gtid-purged=OFF \
    "$MYSQL_DATABASE"' \
  > "$BACKUP_DIR/neostride.sql"
```

`--single-transaction`은 InnoDB 기준으로 서비스 중에도 일관된 snapshot을 만든다. 스키마 변경 직전에는 쓰기 정지를 하고 한 번 더 백업하는 것이 가장 안전하다.

### 7.3 Docker volume 물리 백업

MySQL 컨테이너를 잠시 정지할 수 있다면 가장 단순하다.

```bash
docker stop neostride-mysql
docker run --rm \
  -v mysql_neostride_mysql_data:/volume:ro \
  -v "$BACKUP_DIR":/backup \
  alpine tar czf /backup/mysql-volume.tar.gz -C /volume .
docker start neostride-mysql
```

운영 중 물리 백업은 crash-consistent 문제가 있으므로, 반드시 컨테이너 stop 또는 MySQL hot backup 도구를 사용한다. 개인 홈서버에서는 짧게 멈추고 tar 백업하는 쪽이 더 단순하고 안전하다.

### 7.4 이미지 파일 백업

```bash
rsync -aH --numeric-ids /home/yoonhyeon/neo-stride/uploads/ "$BACKUP_DIR/uploads/"
```

`rsync` 후 파일 수와 총 용량을 기록한다.

```bash
find /home/yoonhyeon/neo-stride/uploads -type f | wc -l
find "$BACKUP_DIR/uploads" -type f | wc -l
du -sh /home/yoonhyeon/neo-stride/uploads "$BACKUP_DIR/uploads"
```

### 7.5 charset/collation 확인

현재 MySQL은 `utf8mb4`, `utf8mb4_unicode_ci`다. 마이그레이션 전후 아래를 확인한다.

```sql
SHOW VARIABLES WHERE Variable_name IN ('character_set_server', 'collation_server');
SHOW TABLE STATUS WHERE Name IN ('community_contents', 'community_interactions');
```

### 7.6 row count 검증

마이그레이션 전후 모두 저장한다.

```sql
SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'community_contents', COUNT(*) FROM community_contents
UNION ALL SELECT 'community_interactions', COUNT(*) FROM community_interactions
UNION ALL SELECT 'relationships', COUNT(*) FROM relationships
UNION ALL SELECT 'running_records', COUNT(*) FROM running_records;
```

이미지 테이블 도입 시:

```sql
SELECT COUNT(*) FROM community_content_images;
SELECT content_id, COUNT(*) FROM community_content_images GROUP BY content_id HAVING COUNT(*) > 3;
```

### 7.7 checksum 또는 sample 검증

MySQL:

```sql
CHECKSUM TABLE community_contents, community_interactions, relationships, running_records;
```

샘플:

```sql
SELECT content_id, author_user_id, content_type, created_at, image
FROM community_contents
ORDER BY content_id DESC
LIMIT 20;
```

파일:

```bash
find /home/yoonhyeon/neo-stride/uploads -type f -exec sha256sum {} \; \
  | sort > "$BACKUP_DIR/uploads.sha256"
```

전체 checksum은 파일 수가 많아지면 오래 걸릴 수 있으므로, 최초 구조 변경 시에는 전체 checksum을 권장하고 이후에는 sample checksum을 병행한다.

### 7.8 최소 downtime 절차

1. 공지 또는 maintenance window 확보
2. 백엔드 쓰기 중지: backend 컨테이너 stop 또는 maintenance mode
3. mysqldump 백업
4. 이미지 rsync 백업
5. DB migration 적용
6. 이미지 thumbnail backfill 실행
7. row count/checksum/sample 검증
8. backend 새 버전 배포
9. smoke test
10. Caddy 정적 파일 직접 서빙 전환

### 7.9 Redis 도입 시 영속화 판단

Redis는 캐시 용도라면 무손실 대상이 아니다. 단일 서버에서는 다음 중 하나를 선택한다.

- 단순 캐시: persistence off 또는 RDB snapshot
- 세션/큐 등 중요 데이터 포함: AOF everysec

피드 조회 개선만 목적이면 Redis 데이터를 백업 대상에 포함하지 않아도 된다.

## 8. 예상 성능 개선 효과

정량값은 운영 데이터 크기와 네트워크 업로드 속도에 따라 다르지만, 기대 방향은 다음과 같다.

- 서버 pagination: 피드 수 N 전체 조회에서 page size 20 고정 조회로 전환. 게시글 수 증가에 따른 첫 화면 DB/JSON 비용을 거의 제거.
- 복합 인덱스: `ALL scan + filesort`를 index range scan으로 전환. 데이터가 수천-수만 건일 때 차이가 크게 난다.
- count aggregation 또는 stats table: 행마다 3개 dependent subquery 반복 제거. interaction이 늘어날수록 효과가 커진다.
- 상태 API 통합: Android 첫 화면 API 4개를 1개로 축소. DB connection, network round trip, JSON parsing 감소.
- 썸네일/WebP: 1.4MB 원본 대신 30-150KB 썸네일 전송 가능. 홈 네트워크 업로드 병목에 가장 직접적인 효과.
- Caddy 직접 정적 서빙: Spring request thread와 Java heap을 이미지 전송에서 분리. 피드 API latency와 동시성 안정성 개선.
- buffer pool 2-4GB: DB working set이 RAM에 머무를 확률 증가. 디스크 read spike 감소.
- 조회 시 이미지 디코딩 제거: 목록 응답마다 발생하는 파일 I/O 및 CPU 디코딩 제거.

가장 체감이 큰 조합은 `썸네일/WebP + 서버 cursor pagination + 상태 API 통합 + 인덱스`다.

## 9. 실제 적용 순서

### Phase 0: 측정부터 켜기

1. MySQL slow query log ON, `long_query_time=0.5`
2. `/api/community/feeds`, `/api/community/feeds/{id}`, `/uploads/...` 응답 시간 측정
3. Caddy access log 또는 reverse proxy log에서 이미지 transfer size 확인
4. Android에서 피드 화면 진입 시 호출 API 수와 waterfall 측정

### Phase 1: 위험 낮은 DB/쿼리 개선

1. DB/이미지 백업
2. 복합 인덱스 추가
3. `EXPLAIN ANALYZE`로 feed query 확인
4. Hikari pool과 MySQL buffer pool 설정 반영
5. slow query log 확인

### Phase 2: API 개선

1. cursor pagination 응답 DTO 추가
2. 목록 projection query 개선
3. 목록 응답에 liked/bookmarked/commented/tagged 포함
4. Android에서 상태 API 3개 제거
5. 기존 endpoint는 호환 유지

### Phase 3: 이미지 개선

1. 업로드 시 원본 저장 + thumbnail WebP 생성
2. 기존 uploads backfill로 thumbnail 생성
3. 목록 API는 thumbnail URL 반환
4. 상세 API는 필요 시 720px 또는 원본 URL 반환
5. Caddy/Nginx 직접 정적 서빙과 cache header 적용

### Phase 4: 구조 정리

1. `community_content_images` 테이블 도입
2. delimiter 기반 image 저장 제거
3. `community_content_stats` 도입
4. delimiter 기반 content_text 저장을 정규 컬럼으로 이전
5. Redis 캐시는 그 이후에도 필요할 때만 도입

## 10. 롤백 전략

### 인덱스 추가 롤백

인덱스 추가는 데이터 변경이 아니므로 비교적 안전하다. 문제가 있으면 drop한다.

```sql
DROP INDEX idx_cc_feed_list ON community_contents;
DROP INDEX idx_ci_content_type ON community_interactions;
DROP INDEX idx_ci_user_type_content ON community_interactions;
```

### API 변경 롤백

- 기존 `/api/community/feeds` 응답 형식을 즉시 바꾸지 않는다.
- 신규 cursor endpoint를 추가하고 Android를 전환한다.
- 문제가 있으면 Android feature flag 또는 앱 설정으로 기존 endpoint를 호출하게 한다.

### 이미지 썸네일 롤백

- 원본 파일은 삭제하지 않는다.
- DB에는 원본 URL을 계속 유지한다.
- thumbnail URL이 없거나 실패하면 원본 URL fallback.
- Caddy 정적 서빙이 문제면 기존 reverse proxy only 설정으로 되돌린다.

### DB 구조 변경 롤백

- `community_content_images`, `community_content_stats`는 additive migration으로 만든다.
- 기존 `community_contents.image`, `content_text`를 바로 삭제하지 않는다.
- 신규 테이블 backfill 후 최소 1-2주 dual-read 또는 fallback 유지.
- 문제가 있으면 신규 코드만 롤백하고 기존 컬럼을 사용한다.

### 전체 복구

1. backend 컨테이너 stop
2. MySQL stop
3. named volume 백업 tar 복구 또는 mysqldump restore
4. uploads 백업 rsync 복구
5. docker compose up
6. row count, sample API, 이미지 URL smoke test

## 11. 추가적으로 측정해야 하는 메트릭

### Backend/API

- `/api/community/feeds` p50/p95/p99 latency
- `/api/community/feeds/{id}` p50/p95/p99 latency
- 응답 payload bytes
- endpoint별 request count
- Spring thread pool active/queued
- Hikari active/idle/pending connection
- JVM heap, GC pause

### MySQL

- slow query log top queries
- `EXPLAIN ANALYZE` for feed list/detail/search
- `Innodb_buffer_pool_reads / Innodb_buffer_pool_read_requests`
- `Threads_connected`, `Threads_running`, `Max_used_connections`
- `Created_tmp_disk_tables`
- table/index size
- rows examined per query

### Disk

- disk type: SSD/HDD
- `iostat -xz 1`
- disk utilization `%util`
- await latency
- Docker volume path의 실제 mount device

### Network

- 홈서버 WAN upload Mbps
- Caddy access log 기준 이미지 transfer bytes
- 이미지 cache hit/miss
- 외부망에서 image TTFB/total time

### Images

- 업로드 원본 평균/최대 byte size
- thumbnail 평균 byte size
- WebP 변환 소요 시간
- thumbnail 생성 실패율
- 깨진 이미지 URL 수

### Android

- 피드 화면 진입 시 API 호출 개수
- 첫 content 표시까지 걸리는 시간
- Glide memory/disk cache hit
- 원본 이미지 다운로드 bytes
- RecyclerView frame drop/jank

## 결론

현재 홈서버 사양은 피드 조회를 감당하기에 부족한 편이 아니다. 병목의 핵심은 서버 자원 부족보다 `전체 피드 조회`, `row별 interaction count`, `인덱스 부족`, `조회 시 이미지 파일 디코딩`, `원본 이미지 전송`, `Spring Boot를 통한 정적 파일 서빙`, `프론트 상태 API 중복 호출`이다.

따라서 가장 현실적인 개선 순서는 다음이다.

1. slow query log와 API/이미지 응답 시간 측정
2. 복합 인덱스 추가
3. cursor pagination 도입
4. 목록 응답에 사용자 interaction 상태 통합
5. 조회 시 이미지 디코딩 제거
6. 썸네일/WebP 생성 및 목록 썸네일 사용
7. Caddy 또는 Nginx로 `/uploads` 직접 정적 서빙
8. MySQL buffer pool 2GB 이상으로 조정
9. 필요 시 stats table과 Redis 캐시 도입
