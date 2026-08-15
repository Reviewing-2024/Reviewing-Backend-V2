# Reviewing-V2 프로젝트

## 최상위 규칙
- 작업할때마다 바뀐 내용, 새로추가된 내용 CLAUDE.md에 정리 + 세부 내용은 docs/ 파일에 추가

## 프로젝트 개요
- 강의 리뷰 플랫폼 (노마드코더, 인프런, 코드잇, 코딩애플, 패스트캠퍼스 크롤링 + 리뷰)
- **Tech Stack**: Kotlin + Spring Boot 3.4.1 + Spring Batch 5.x + PostgreSQL + Elasticsearch
- **Java**: 21 / **인증**: Kakao OAuth2 + JWT / **검색**: Elasticsearch + Spring AI (하이브리드 + RAG)

## 프로젝트 구조

```
src/main/kotlin/my/reviewing/reviewing_V2/
├── global/          # ApiResponse, SecurityConfig, JWTFilter/Util, GlobalExceptionHandler
├── member/          # AuthController, MemberController, CustomOAuth2MemberService
├── course/          # roller(찜 포함), CourseService, CourseWish
├── crawling/
│   ├── entity/      # Platform, Course, Category, SubCategory, CategoryCourse, SubCategoryCourse
│   ├── repository/
│   ├── dto/         # CrawlingCourseDto (인프런/코드잇/패스트캠퍼스 공유)
│   └── crawlingBatch/
│       ├── nomadcoders/  # NomadcodersCrawlingBatch, NomadcodersReader
│       ├── inflearn/     # InflearnCrawlingBatch, InflearnReader
│       ├── codeit/       # CodeitCrawlingBatch, CodeitReader
│       ├── codingapple/  # CodingappleCrawlingBatch, CodingappleReader
│       └── fastcampus/   # FastcampusCrawlingBatch, FastcampusReader
├── review/          # ReviewController, ReviewService, Review, ReviewStateType(PENDING/APPROVED/REJECTED), ReviewLike, ReviewLikeType(LIKE/DISLIKE)
├── admin/           # AdminController, AdminService
└── search/          # SearchService (하이브리드 검색)
```

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | /api/v1/auth/access | Access Token 발급 (refresh 쿠키) |
| POST | /api/v1/auth/reissue | Access + Refresh 재발급 |
| POST | /api/v1/auth/logout | 로그아웃 |
| GET | /api/v1/courses | 강의 목록 (platform, category, subCategories, sort, page, size) |
| POST | /api/v1/courses/{courseId}/wish | 찜 추가 (JWT) |
| DELETE | /api/v1/courses/{courseId}/wish | 찜 취소 (JWT) |
| GET | /api/v1/platforms | 전체 플랫폼 목록 |
| POST | /api/v1/crawling/platform | 플랫폼 생성 |
| POST | /api/v1/crawling/courses | 크롤링 실행 (jobName 파라미터) |
| GET | /api/v1/crawling/status/{jobId} | 크롤링 상태 조회 |
| POST | /api/v1/crawling/categories/{platform} | 카테고리 저장 (inflearn/codeit/fastcampus) |
| POST | /api/v1/reviews/{courseId} | 리뷰 작성 (JWT, multipart) |
| GET | /api/v1/reviews/{courseId} | 리뷰 조회 (무한스크롤) |
| POST | /api/v1/reviews/{reviewId}/like | 리뷰 좋아요 (JWT, 싫어요 있으면 자동 전환) |
| DELETE | /api/v1/reviews/{reviewId}/like | 리뷰 좋아요 취소 (JWT) |
| POST | /api/v1/reviews/{reviewId}/dislike | 리뷰 싫어요 (JWT, 좋아요 있으면 자동 전환) |
| DELETE | /api/v1/reviews/{reviewId}/dislike | 리뷰 싫어요 취소 (JWT) |
| GET | /api/v1/admin/reviews | 관리자 리뷰 목록 |
| PATCH | /api/v1/admin/reviews/{id}/approve | 리뷰 승인 |
| PATCH | /api/v1/admin/reviews/{id}/reject | 리뷰 거절 |
| GET | /api/v1/search | 강의 검색 (하이브리드: keyword + vector) |
| GET | /api/v1/members/me/wishes | 내 찜 강의 목록 (JWT, page/size) |
| GET | /api/v1/members/me/reviews | 내 리뷰 목록 (JWT, state 선택, page/size) |
| PATCH | /api/v1/members/me/nickname | 닉네임 변경 (JWT) |

## 핵심 설정

**크롤링 Job 이름:** `nomadcodersCrawlingJob` / `inflearnCrawlingJob` / `codeitCrawlingJob` / `codingappleCrawlingJob` / `fastcampusCrawlingJob`

**Elasticsearch 인덱스:** `courses` (id, platform, title, teacher, embedding[512])
- `initialize-schema: false` — @PostConstruct로 커스텀 인덱스 생성
- 임베딩: OpenAI text-embedding-3-large, dim=512
- 유사도: dot_product (정규화 벡터라 cosine과 동일)
- 검색: BM25(multi_match + fuzziness) + KNN 선형 합산 (RRF는 Platinum 유료라 미사용)
- 검색 순서: POST /api/v1/search/embeddings 로 임베딩 배치 먼저 실행 필요

**Reader 클래스는 모두 `open` 키워드 필요** (@StepScope CGLIB 프록시 때문)

**CrawlingCourseDto 패키지:** `my.reviewing.reviewing_V2.crawling.dto.CrawlingCourseDto`

## TODO

- [x] 플랫폼별 크롤러 (노마드코더, 인프런, 코드잇, 코딩애플, 패스트캠퍼스)
- [x] 강의 조회 API (QueryDSL 필터 + 페이지네이션)
- [x] 리뷰 기능 (작성/조회/승인/거절)
- [x] 강의 찜 기능
- [x] 리뷰 좋아요/싫어요 기능 (ReviewLike 엔티티, 전환 시 자동 처리)
- [x] 관리자 기능
- [x] Elasticsearch 인덱스 + 임베딩 배치
- [x] 하이브리드 검색 API (BM25 + KNN 선형 합산, fuzziness 포함)
- [ ] RAG 챗봇 추천 API
- [ ] 테스트 코드 작성

## 세부 문서

- [크롤링 개발 노트](docs/crawling-notes.md) — Spring Batch 구조, 플랫폼별 크롤링 상세, 버그 수정
- [기능 개발 노트](docs/feature-notes.md) — JWT 버그, 리뷰/찜/관리자, Elasticsearch/Spring AI, PageImpl 등
