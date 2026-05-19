# Neo-Stride Frontend API Specification

작성일: 2026-05-14
최종 점검: 2026-05-19 07:27 UTC

이 문서는 현재 로컬 Android 프로젝트 `Neo-Stride/Neo-Stride`의 Retrofit API 계약을 기준으로 백엔드 `Neo-Stride/Neo-Stride-BE` 구현 상태를 대조한 API 명세입니다. 이번 버전은 Android `main` 브랜치 `d7f15e2`와 백엔드 `codex/prepare-backend-readiness` 브랜치 `de506d9` 기준입니다.

## 이번 업데이트 요약

2026-05-19 기준 Android `app/src/main/java/**/*.java`의 Retrofit annotation을 재추출했습니다.

- Android Retrofit annotation: 총 65개
- Android 고유 API 계약: 총 63개
  - `POST /api/auth/signup`은 JSON 가입과 multipart 사진 포함 가입이 같은 endpoint에 중복 선언되어 고유 API에서는 1개로 계산합니다.
  - `GET /users/{userId}/badge`는 `BadgeService`, `RunnerPageService` 양쪽에 중복 선언되어 고유 API에서는 1개로 계산합니다.
- 백엔드 Controller 구현 API: 총 37개
- Android 현 계약과 직접 method/path가 일치하는 API: 31개
- Android 현 계약 기준 백엔드 미구현 또는 경로 불일치 API: 32개
- 백엔드에는 있지만 Android 현재 소스에는 없는 alias/legacy API: 6개

핵심 변경:

- AI 피드백 API `POST /api/coaching/plans/{plan_day_id}/feedback`를 현재 Android/Backend 기준으로 명시했습니다.
- Android 최신 main에 추가된 피드 상세/수정/삭제/좋아요/북마크/댓글, 팁 상세/수정/삭제/좋아요/북마크/댓글, 검색, 알림 API를 반영했습니다.
- 기존 문서가 `/feeds`, `/api/tips`를 Android API처럼 적고 있던 부분을 바로잡았습니다. 현재 Android는 `/api/community/feeds`, `/api/community/tips`를 호출하고, 백엔드는 일부 legacy 경로 `/feeds`, `/api/tips`만 제공합니다.

## 공통 설정

### Base URL

Android Base URL은 Gradle `BuildConfig.BASE_URL`로 주입됩니다.

- 설정 위치: `Neo-Stride/app/build.gradle.kts`
- 기본값: `"http://10.0.2.2:8080/"`
- 로컬 변경 방법: `Neo-Stride/local.properties`에 `BASE_URL` 값을 설정

```properties
BASE_URL="http://10.0.2.2:8080/"
```

주의: Retrofit path가 `/api/...`처럼 `/`로 시작하는 API와 `users/...`, `community/...`, `api/...`처럼 상대 path인 API가 섞여 있습니다. Base URL에 path component가 들어간 환경에서는 Retrofit URL 결합 방식 차이가 발생할 수 있습니다.

### Retrofit Client 사용 방식

| 영역 | Retrofit 생성 위치 | 공통 Interceptor/Auth Header 적용 |
| --- | --- | --- |
| 러닝/코칭/피드/팁/계정/마이페이지/러너페이지/친구/배지/검색/알림 | `common/network/ApiClient.java` | 적용됨 |
| 인증/회원가입/로그인 | `feature/auth/repository/AuthRepository.java` | 적용 안 됨. 별도 Retrofit을 직접 생성함 |

`ApiClient`를 사용하는 요청은 저장된 access token이 있을 때 `Authorization: Bearer <access_token>` 헤더를 추가합니다. 일부 최신 커뮤니티/알림 API는 메서드 파라미터로 `X-User-Id` 헤더도 직접 전달합니다.

`ApiClient`는 아래 경로 문자열을 포함하는 요청에는 Authorization 헤더를 붙이지 않습니다.

- `/auth/login`
- `/auth/signup`
- `/auth/email`
- `/auth/find`
- `/auth/reset`

주의: `AuthRepository`는 `ApiClient`를 사용하지 않으므로 로그인/회원가입 요청에는 위 Interceptor, BODY logging, 15초 timeout 설정이 적용되지 않습니다.

### Backend Time Zone

백엔드는 Asia/Seoul(KST, UTC+09:00)을 기본 Time Zone으로 사용합니다.

| 범위 | 설정 위치 | 값 |
| --- | --- | --- |
| JVM 기본 TimeZone | `ServerApplication.DEFAULT_TIME_ZONE` / static initializer | `Asia/Seoul` |
| Jackson 직렬화 TimeZone | `spring.jackson.time-zone` | `Asia/Seoul` |
| Spring Boot 로그 타임스탬프 | `logging.pattern.dateformat` | `yyyy-MM-dd'T'HH:mm:ss.SSSXXX,Asia/Seoul` |
| MySQL JDBC 세션 TimeZone | `spring.datasource.url` | `serverTimezone=Asia/Seoul` |

## API 목록

| 영역 | Method | Endpoint | Android 선언 위치 | 백엔드 상태 |
| --- | --- | --- | --- | --- |
| 계정/프로필/배지 | `GET` | `/users/me/account` | `com/neostride/app/feature/account/api/AccountApi.java:17 getAccountInfo()` | 구현됨 |
| 계정/프로필/배지 | `PATCH` | `/users/me/nickname` | `com/neostride/app/feature/account/api/AccountApi.java:21 updateNickname()` | 구현됨 |
| 계정/프로필/배지 | `DELETE` | `/users/me` | `com/neostride/app/feature/account/api/AccountApi.java:25 deleteAccount()` | 구현됨 |
| 인증 | `POST` | `/api/auth/login` | `com/neostride/app/feature/auth/api/AuthApi.java:19 login()` | 구현됨 |
| 인증 | `POST` | `/api/auth/signup` | `com/neostride/app/feature/auth/api/AuthApi.java:23 signup()`, `com/neostride/app/feature/auth/api/AuthApi.java:29 signupWithPhoto()` | 구현됨. multipart 가입은 request content type 불일치 주의 |
| 계정/프로필/배지 | `GET` | `/users/me/badge` | `com/neostride/app/feature/badge/api/BadgeService.java:15 getBadgeDetail()` | 구현됨 |
| 계정/프로필/배지 | `GET` | `/users/{userId}/badge` | `com/neostride/app/feature/badge/api/BadgeService.java:19 getBadgeDetailByUserId()`, `com/neostride/app/feature/community/runnerpage/api/RunnerPageService.java:25 getRunnerBadge()` | 구현됨 |
| 피드 | `POST` | `/api/community/feeds` | `com/neostride/app/feature/community/feed/api/FeedApi.java:44 uploadFeed()` | 경로 불일치: 백엔드는 POST /feeds만 구현 |
| 피드 | `GET` | `/api/community/feeds` | `com/neostride/app/feature/community/feed/api/FeedApi.java:60 getFeedList()` | 경로 불일치: 백엔드는 GET /feeds만 구현 |
| 피드 | `GET` | `/api/community/feeds/{feedId}` | `com/neostride/app/feature/community/feed/api/FeedApi.java:72 getFeedDetail()` | 백엔드 미구현/미확인 |
| 친구 | `GET` | `/api/community/friends` | `com/neostride/app/feature/community/feed/api/FeedApi.java:92 getFriendList()` | 경로 불일치: 백엔드는 GET /community/friends 및 /api/relationships 제공 |
| 피드 | `POST` | `/api/community/feeds/{feedId}/likes` | `com/neostride/app/feature/community/feed/api/FeedApi.java:102 toggleFeedLike()` | 백엔드 미구현/미확인 |
| 피드 | `POST` | `/api/community/feeds/{feedId}/bookmarks` | `com/neostride/app/feature/community/feed/api/FeedApi.java:111 toggleFeedBookmark()` | 백엔드 미구현/미확인 |
| 피드 | `POST` | `/api/community/feeds/{feedId}/comments` | `com/neostride/app/feature/community/feed/api/FeedApi.java:116 createFeedComment()` | 백엔드 미구현/미확인 |
| 피드 | `DELETE` | `/api/community/feeds/{feedId}` | `com/neostride/app/feature/community/feed/api/FeedApi.java:126 deleteFeed()` | 백엔드 미구현/미확인 |
| 피드 | `PUT` | `/api/community/feeds/{feedId}` | `com/neostride/app/feature/community/feed/api/FeedApi.java:135 updateFeed()` | 백엔드 미구현/미확인 |
| 피드 | `PUT` | `/api/community/feeds/{feedId}/comments/{commentId}` | `com/neostride/app/feature/community/feed/api/FeedApi.java:145 updateFeedComment()` | 백엔드 미구현/미확인 |
| 피드 | `DELETE` | `/api/community/feeds/{feedId}/comments/{commentId}` | `com/neostride/app/feature/community/feed/api/FeedApi.java:156 deleteFeedComment()` | 백엔드 미구현/미확인 |
| 친구 | `GET` | `/community/friends` | `com/neostride/app/feature/community/friend/api/FriendApi.java:23 getFriendList()` | 구현됨 |
| 친구 | `POST` | `/community/friends/action` | `com/neostride/app/feature/community/friend/api/FriendApi.java:28 updateRelationship()` | 구현됨 |
| 친구 | `GET` | `/community/friends/user/{userId}` | `com/neostride/app/feature/community/friend/api/FriendApi.java:33 getUserFriendList()` | 구현됨 |
| 계정/프로필/배지 | `GET` | `/users/me/profile` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:28 getUserProfile()` | 구현됨 |
| 계정/프로필/배지 | `PATCH` | `/users/me/status` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:32 updateStatusMessage()` | 구현됨 |
| 계정/프로필/배지 | `PATCH` | `/users/me/profile-image` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:37 updateProfileImage()` | 구현됨 |
| 마이페이지 | `GET` | `/community/contents/me` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:41 getMyFeeds()` | 구현됨 |
| 마이페이지 | `GET` | `/community/contents/tagged` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:45 getTaggedFeeds()` | 구현됨 |
| 마이페이지 | `GET` | `/community/contents/comments` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:49 getCommentedFeeds()` | 구현됨 |
| 마이페이지 | `GET` | `/community/contents/likes` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:53 getLikedFeeds()` | 구현됨 |
| 마이페이지 | `GET` | `/community/contents/bookmarks` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:57 getBookmarkedFeeds()` | 구현됨 |
| 팁 | `GET` | `/api/community/tips/me` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:61 getMyTips()` | 백엔드 미구현/미확인 |
| 기타 커뮤니티 | `POST` | `/community/bookmark/{contentId}` | `com/neostride/app/feature/community/mypage/api/MyPageService.java:65 toggleBookmark()` | 구현됨 |
| 계정/프로필/배지 | `GET` | `/users/{userId}/profile` | `com/neostride/app/feature/community/runnerpage/api/RunnerPageService.java:21 getRunnerProfile()` | 구현됨 |
| 마이페이지 | `GET` | `/community/contents/user/{userId}` | `com/neostride/app/feature/community/runnerpage/api/RunnerPageService.java:29 getRunnerFeeds()` | 구현됨 |
| 팁 | `GET` | `/community/tips/user/{userId}` | `com/neostride/app/feature/community/runnerpage/api/RunnerPageService.java:33 getRunnerTips()` | 백엔드 미구현/미확인 |
| 검색 | `GET` | `/api/community/search/feeds` | `com/neostride/app/feature/community/search/api/SearchApi.java:28 searchFeeds()` | 백엔드 미구현/미확인 |
| 검색 | `GET` | `/api/community/search/tips` | `com/neostride/app/feature/community/search/api/SearchApi.java:41 searchTips()` | 백엔드 미구현/미확인 |
| 검색 | `GET` | `/api/community/search/profiles` | `com/neostride/app/feature/community/search/api/SearchApi.java:53 searchProfiles()` | 백엔드 미구현/미확인 |
| 검색 | `GET` | `/api/community/search/friends` | `com/neostride/app/feature/community/search/api/SearchApi.java:64 searchFriends()` | 백엔드 미구현/미확인 |
| 검색 | `GET` | `/api/community/search/top-profiles` | `com/neostride/app/feature/community/search/api/SearchApi.java:74 getTopProfiles()` | 백엔드 미구현/미확인 |
| 검색 | `GET` | `/api/community/search/my-friends` | `com/neostride/app/feature/community/search/api/SearchApi.java:84 getMyFriends()` | 백엔드 미구현/미확인 |
| 팁 | `POST` | `/api/community/tips` | `com/neostride/app/feature/community/tip/api/TipApi.java:43 uploadTip()` | 경로 불일치: 백엔드는 POST /api/tips만 구현 |
| 팁 | `GET` | `/api/community/tips` | `com/neostride/app/feature/community/tip/api/TipApi.java:54 getTips()` | 경로 불일치: 백엔드는 GET /api/tips만 구현 |
| 팁 | `GET` | `/api/community/tips/{tipId}` | `com/neostride/app/feature/community/tip/api/TipApi.java:61 getTipDetail()` | 백엔드 미구현/미확인 |
| 팁 | `POST` | `/api/community/tips/{tipId}/likes` | `com/neostride/app/feature/community/tip/api/TipApi.java:70 toggleTipLike()` | 백엔드 미구현/미확인 |
| 팁 | `POST` | `/api/community/tips/{tipId}/bookmarks` | `com/neostride/app/feature/community/tip/api/TipApi.java:79 toggleTipBookmark()` | 백엔드 미구현/미확인 |
| 팁 | `POST` | `/api/community/tips/{tipId}/comments` | `com/neostride/app/feature/community/tip/api/TipApi.java:88 createTipComment()` | 백엔드 미구현/미확인 |
| 팁 | `DELETE` | `/api/community/tips/{tipId}` | `com/neostride/app/feature/community/tip/api/TipApi.java:98 deleteTip()` | 백엔드 미구현/미확인 |
| 팁 | `PUT` | `/api/community/tips/{tipId}` | `com/neostride/app/feature/community/tip/api/TipApi.java:107 updateTip()` | 백엔드 미구현/미확인 |
| 팁 | `PUT` | `/api/community/tips/{tipId}/comments/{commentId}` | `com/neostride/app/feature/community/tip/api/TipApi.java:117 updateTipComment()` | 백엔드 미구현/미확인 |
| 팁 | `DELETE` | `/api/community/tips/{tipId}/comments/{commentId}` | `com/neostride/app/feature/community/tip/api/TipApi.java:128 deleteTipComment()` | 백엔드 미구현/미확인 |
| 코칭/AI | `POST` | `/api/coaching/goals` | `com/neostride/app/feature/main/coaching/api/CoachingApi.java:29 createGoal()` | 구현됨 |
| 코칭/AI | `GET` | `/api/coaching/goals/active` | `com/neostride/app/feature/main/coaching/api/CoachingApi.java:33 getActiveGoal()` | 구현됨 |
| 코칭/AI | `GET` | `/api/coaching/plans/today` | `com/neostride/app/feature/main/coaching/api/CoachingApi.java:37 getTodayPlan()` | 구현됨 |
| 코칭/AI | `POST` | `/api/coaching/plans/{plan_day_id}/feedback` | `com/neostride/app/feature/main/coaching/api/CoachingApi.java:41 requestFeedback()` | 구현됨 |
| 코칭/AI | `DELETE` | `/api/coaching/goals/{goal_id}` | `com/neostride/app/feature/main/coaching/api/CoachingApi.java:48 deleteGoal()` | 구현됨 |
| 코칭/AI | `PATCH` | `/api/coaching/goals/{goal_id}/status` | `com/neostride/app/feature/main/coaching/api/CoachingApi.java:52 updateGoalStatus()` | 구현됨 |
| 러닝 | `POST` | `/api/running/records` | `com/neostride/app/feature/main/running/api/RunningApi.java:22 saveRunningRecord()` | 구현됨 |
| 러닝 | `GET` | `/api/running/records/user/{user_id}` | `com/neostride/app/feature/main/running/api/RunningApi.java:26 fetchUserRecords()` | 구현됨 |
| 러닝 | `GET` | `/api/running/records` | `com/neostride/app/feature/main/running/api/RunningApi.java:30 getMonthlyRecords()` | 구현됨 |
| 러닝 | `GET` | `/api/running/records/{record_id}` | `com/neostride/app/feature/main/running/api/RunningApi.java:37 getRecordDetail()` | 구현됨 |
| 알림 | `GET` | `/api/notifications` | `com/neostride/app/feature/notification/api/NotificationApi.java:25 getNotifications()` | 백엔드 미구현/미확인 |
| 알림 | `DELETE` | `/api/notifications/{notificationId}` | `com/neostride/app/feature/notification/api/NotificationApi.java:34 deleteNotification()` | 백엔드 미구현/미확인 |
| 알림 | `DELETE` | `/api/notifications` | `com/neostride/app/feature/notification/api/NotificationApi.java:43 deleteAllNotifications()` | 백엔드 미구현/미확인 |

## 인증 API

### POST /api/auth/signup

Android 선언:

- `AuthApi.signup(@Body SignupRequest request)`
- `AuthApi.signupWithPhoto(@Part email, @Part name, @Part password, @Part profilePhoto)`
- `AuthRepository`는 `ApiClient`가 아니라 별도 Retrofit을 직접 생성합니다.

JSON Request:

```json
{
  "email": "runner@example.com",
  "name": "홍길동",
  "password": "***"
}
```

Response 예시:

```json
{
  "status": "success",
  "message": "회원가입이 완료되었습니다.",
  "user_id": 1,
  "email": "runner@example.com",
  "name": "홍길동"
}
```

백엔드 상태:

- `AuthController.signup()` 구현됨.
- 성공: `201 Created`
- 이메일 형식/필수값 오류: `400 Bad Request`
- 중복 이메일: `409 Conflict`
- 주의: Android에는 multipart 사진 포함 가입 선언이 있지만, 현재 백엔드 `AuthController.signup()`은 JSON `SignupRequest`만 받습니다. multipart 가입은 method/path는 같지만 request content type이 달라서 별도 구현/검증이 필요합니다.

### POST /api/auth/login

Request:

```json
{
  "email": "runner@example.com",
  "password": "***"
}
```

Response 예시:

```json
{
  "status": "success",
  "message": "로그인에 성공했습니다.",
  "user_id": 1,
  "email": "runner@example.com",
  "name": "홍길동",
  "nickname": "홍길동",
  "access_token": "***",
  "refresh_token": "***"
}
```

백엔드 상태: `AuthController.login()` 구현됨. 성공 `200 OK`, 인증 실패 `401 Unauthorized`.

## 러닝 기록 API

Android DTO 기준 `RunningRecordRequest`:

```json
{
  "user_id": 1,
  "plan_id": null,
  "total_distance": 3.25,
  "duration": 1240,
  "pace": 342,
  "calories": 235.69,
  "route_detail": "",
  "gps_traces": [
    {
      "latitude": 37.5665,
      "longitude": 126.978,
      "time": "2026-04-28 09:30:12",
      "heart_rate": 150,
      "cadence": 170
    }
  ],
  "badge": "bronze"
}
```

Backend DTO 기준:

- `pace`는 현재 백엔드에서 `BigDecimal` minutes/km로 저장/응답합니다. 최근 수정으로 DB `running_records.pace`는 `DECIMAL(8,2)`입니다.
- Android `RunningRecordRequest.pace`는 현재 `int`라서 초 단위/소수 페이스 입력 계약이 아직 백엔드와 완전히 일치하지 않습니다. Android 응답 DTO `RunningRecordResponse.pace`는 `float`입니다.
- `GpsTraceRequest`는 `heart_rate`, `cadence`를 optional로 포함합니다.

Endpoints:

| Method | Endpoint | Android | 백엔드 |
| --- | --- | --- | --- |
| `POST` | `/api/running/records` | `RunningApi.saveRunningRecord()` | `RunningRecordController.saveRunningRecord()` |
| `GET` | `/api/running/records/user/{user_id}` | `RunningApi.fetchUserRecords()` | `RunningRecordController.fetchUserRecords()` |
| `GET` | `/api/running/records?year=&month=` | `RunningApi.getMonthlyRecords()` | `RunningRecordController.getMonthlyRecords()` |
| `GET` | `/api/running/records/{record_id}` | `RunningApi.getRecordDetail()` | `RunningRecordController.getRecordDetail()` |

## 코칭/AI 플랜/AI 피드백 API

이 영역은 `CoachingApi`와 `CoachingRepository`가 `ApiClient`를 통해 호출합니다.

### OpenAI integration 동작

백엔드에는 실제 OpenAI 호출 클라이언트가 구현되어 있습니다.

- 구현 위치: `com.neostride.server.coaching.ai.OpenAiCoachingClient`
- 설정 위치: `src/main/resources/application.properties`
- 환경변수:
  - `OPENAI_API_KEY`: 설정되어 있으면 실제 OpenAI API 호출 시도
  - `OPENAI_BASE_URL`: 기본값 `https://api.openai.com/v1`
  - `OPENAI_MODEL`: 기본값 `gpt-4o-mini`
- 실패/미설정 시 동작: API 에러를 사용자에게 직접 노출하지 않고 deterministic fallback 플랜/피드백을 생성합니다.

### GoalRequest

```json
{
  "user_id": 1,
  "period_type": "1month",
  "custom_weeks": 0,
  "running_days": ["mon", "wed", "fri"],
  "goal_distance_km": 5.0,
  "goal_pace_min_per_km": 6.5,
  "start_date": "2026-04-30"
}
```

### GoalResponse

```json
{
  "goal_id": 1,
  "has_active_goal": true,
  "status": "active",
  "goal": {
    "goal_id": 1,
    "period_type": "1month",
    "custom_weeks": 0,
    "running_days": ["mon", "wed", "fri"],
    "goal_distance_km": 5.0,
    "goal_pace_min_per_km": 6.5,
    "start_date": "2026-04-30",
    "end_date": "2026-05-30",
    "created_at": "2026-04-30T10:00:00",
    "is_active": true,
    "is_achieved": false
  },
  "plan_days": []
}
```

### TodayPlanResponse / PlanDayResponse

```json
{
  "has_plan": true,
  "plan_day": {
    "plan_day_id": 10,
    "plan_date": "2026-05-05",
    "day_distance_km": 3.0,
    "day_pace_min_per_km": 7.0,
    "description": "가볍게 조깅하세요.",
    "is_completed": false,
    "ai_feedback_comment": null,
    "ai_feedback_at": null,
    "actual_duration_sec": 0
  },
  "goal": null
}
```

### AI FeedbackRequest / FeedbackResponse

Request:

```json
{
  "plan_day_id": 10,
  "actual_distance_km": 3.2,
  "actual_time_sec": 1240,
  "actual_pace_min_per_km": 6.45
}
```

Response:

```json
{
  "plan_day_id": 10,
  "is_completed": true,
  "ai_feedback_comment": "목표보다 안정적인 페이스로 완주했습니다.",
  "ai_feedback_at": "2026-05-05T20:30:00"
}
```

주의:

- `FeedbackRequest`는 `plan_day_id`를 Path와 Body에 중복해서 보냅니다. 백엔드는 두 값이 다르면 `400 Bad Request`를 반환합니다.
- 현재 구현은 AI 호출 성공 여부를 응답 필드로 구분하지 않습니다. OpenAI 실패 시에도 fallback 피드백 문자열이 저장될 수 있습니다.
- `OpenAiCoachingClient.generateFeedback()`는 OpenAI 응답 JSON의 `ai_feedback_comment` 필드를 읽습니다.

Endpoints:

| Method | Endpoint | Android | 백엔드 |
| --- | --- | --- | --- |
| `POST` | `/api/coaching/goals` | `CoachingApi.createGoal()` | `CoachingController.createGoal()` |
| `GET` | `/api/coaching/goals/active?user_id=` | `CoachingApi.getActiveGoal()` | `CoachingController.getActiveGoal()` |
| `GET` | `/api/coaching/plans/today?user_id=` | `CoachingApi.getTodayPlan()` | `CoachingController.getTodayPlan()` |
| `POST` | `/api/coaching/plans/{plan_day_id}/feedback` | `CoachingApi.requestFeedback()` | `CoachingController.requestFeedback()` |
| `DELETE` | `/api/coaching/goals/{goal_id}` | `CoachingApi.deleteGoal()` | `CoachingController.deleteGoal()` |
| `PATCH` | `/api/coaching/goals/{goal_id}/status` | `CoachingApi.updateGoalStatus()` | `CoachingController.updateGoalStatus()` |

## 검색 API

Android `SearchApi`는 아래 검색 API를 호출합니다. 현재 백엔드 Controller에는 해당 `/api/community/search/*` endpoint가 없습니다.

- `GET /api/community/search/feeds?keyword=&page=&size=`
- `GET /api/community/search/tips?keyword=&category=&page=&size=`
- `GET /api/community/search/profiles?keyword=&page=&size=`
- `GET /api/community/search/friends?keyword=`
- `GET /api/community/search/top-profiles?page=&size=`
- `GET /api/community/search/my-friends`

## 알림 API

Android `NotificationApi`는 아래 알림 API를 호출합니다. 현재 백엔드 Controller에는 해당 endpoint가 없습니다.

- `GET /api/notifications` with optional `X-User-Id`
- `DELETE /api/notifications/{notificationId}`
- `DELETE /api/notifications` with optional `X-User-Id`

## 백엔드 구현 대조 결과

### Android 대비 미구현/경로 불일치 backend endpoint

최신 Android 기준 백엔드에 직접 대응되지 않는 API는 32개입니다. 대부분 `/api/community/feeds`, `/api/community/tips`, `/api/community/search`, `/api/notifications` 계열입니다. 상세 목록은 상단 API 목록의 `백엔드 상태` 컬럼을 기준으로 확인합니다.

### 백엔드 legacy/extra endpoint

현재 Android main 소스에는 직접 선언이 없지만 백엔드가 제공합니다.

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/relationships` | legacy 친구 목록 alias |
| `POST` | `/api/relationships/action` | legacy 친구 action alias |
| `POST` | `/feeds` | legacy 피드 업로드 |
| `GET` | `/feeds` | legacy 피드 목록 |
| `POST` | `/api/tips` | legacy 팁 업로드 |
| `GET` | `/api/tips` | legacy 팁 목록 |

## 확인된 범위와 주의사항

- 확인/문서 업데이트 시각: 2026-05-19 07:27 UTC
- Frontend 검색 대상: `Neo-Stride/Neo-Stride/app/src/main/java/**/*.java`
- Backend 검색 대상: `Neo-Stride/Neo-Stride-BE/src/main/java/**/*.java`
- Android repo 상태: `main...origin/main`, HEAD `d7f15e2`
- Backend repo 상태: `codex/prepare-backend-readiness...origin/codex/prepare-backend-readiness`, HEAD `de506d9`
- 부모 디렉터리 `/home/yoonhyeon/projects/Neo-Stride`는 git repository가 아니므로 원본 `docs/frontend-api-spec.md` 자체는 현재 어떤 git repo에도 속하지 않습니다. 푸시 가능한 산출물로 백엔드 repo의 `docs/frontend-api-spec.md`에도 동일 내용을 저장했습니다.
- 현재 백엔드 작업 트리에는 기존 untracked `backups/` 디렉터리가 있으며 이 문서 업데이트에서는 건드리지 않았습니다.
- 최신 Android 커뮤니티 API는 `/api/community/...` namespace로 확장되었지만, 백엔드는 아직 일부 이전 경로(`/feeds`, `/api/tips`, `/api/relationships`)만 제공합니다. 앱 통합 전에 backend route alias 또는 신규 Controller 구현이 필요합니다.
- AI 피드백 API는 구현되어 있지만, OpenAI 호출 성공/실패 여부를 응답에서 구분하지 않습니다. 운영 검증을 위해서는 로그 또는 `source: ai|fallback` 같은 추적 필드 추가를 권장합니다.
