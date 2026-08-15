# 기능 개발 노트

## 하이브리드 검색 (Elasticsearch)

### 구조

```
search/
├── controller/SearchController.kt   # GET /api/v1/search, POST /api/v1/search/embeddings
├── service/SearchService.kt         # 하이브리드 검색 로직
├── batch/CourseEmbeddingBatch.kt    # 임베딩 생성 배치 (courseEmbeddingJob)
└── dto/CourseDocument.kt            # ES 저장 문서 (id, platform, title, teacher, embedding)
```

### API

```
POST /api/v1/search/embeddings        # 임베딩 배치 실행 (검색 전 필수)
GET  /api/v1/search?keyword=리액트&page=0&size=10   # 하이브리드 검색
```

응답: `CourseResponseDto` (강의 목록 API와 동일, 로그인 시 wished 포함)

### 검색 흐름

```
keyword → OpenAI text-embedding-3-large (dim=512) → float[]
        ↓
ES: multi_match(BM25 + fuzziness) + knn(벡터) → 선형 합산
        ↓
_id 목록 추출 → PostgreSQL findAllById → ES 순서 유지하며 DTO 변환
```

### 핵심 코드

**BM25 키워드 검색:**
```kotlin
q.multiMatch { mm ->
    mm.query(keyword)
        .fields("title^2", "title.korean^2", "title.english", "teacher", "teacher.korean")
        .fuzziness("AUTO")  // 오타 허용 (3글자 이상 → 1자, 6글자 이상 → 2자)
        .prefixLength(2)    // 앞 2글자는 정확히 일치 (성능 보호)
}
```

**KNN 벡터 검색:**
```kotlin
knn.field("embedding")
    .queryVector(vector.toList())
    .k(size)
    .numCandidates(size * 10)
```

**title^2 이유:** 제목 매칭이 teacher 필드 매칭보다 중요 → 제목에 키워드 있는 강의 우선

**fuzziness("AUTO") 동작:**
| 키워드 길이 | 허용 오타 |
|------------|---------|
| 1~2글자 | 0 (정확) |
| 3~5글자 | 1 |
| 6글자~ | 2 |

**한국어 + fuzziness:** nori 분석기는 형태소 단위 토큰화 → 오타 시 토큰 자체가 달라져 fuzziness 효과 제한적. `title.korean`보다 기본 `title` 필드에서 효과적.

### RRF vs 선형 합산

**RRF(Reciprocal Rank Fusion):** 순위 기반 합산 → Platinum 유료 라이선스 필요 → **미사용**

**선형 합산 (현재):** `query + knn` 동시 사용 시 ES가 두 점수를 직접 더함
- 문제: BM25 점수(1~20)가 KNN 점수(0~1)보다 훨씬 커서 BM25가 결과를 지배할 수 있음
- Basic 라이선스 무료 지원

### 고도화 방향

**1. KNN boost로 균형 맞추기 (간단):**
```kotlin
.knn { knn -> knn...boost(10.0f) }  // KNN 점수 10배 → BM25와 균형
// 5.0f: BM25 약간 우세 / 10.0f: 균형 / 15.0f: KNN 우세
// 실험적으로 조정 필요
```

**2. 두 쿼리 분리 후 직접 정규화 합산 (정확, 복잡):**
```
ES 요청 1: BM25만 → 점수 0~1 정규화
ES 요청 2: KNN만  → 점수 0~1 정규화
최종 점수 = BM25 × 0.5 + KNN × 0.5
```

**3. 임베딩 배치 개선:**
- 현재: 전체 강의 재임베딩 (중복 저장 없음, 매번 덮어씀)
- 개선: ES에 존재 여부 체크 후 새 강의만 임베딩

**4. 검색 결과에 카테고리 정보 포함:**
- 현재: ES → id → PostgreSQL 조회 (기본 Course 필드만)
- 개선: SubCategory/Category 정보도 응답에 포함

---

## JWT 보안 버그 수정 (jjwt 0.12.x)

**문제:** `parseSignedClaims()`는 만료 토큰에 대해 `isExpired()` return 전에 `ExpiredJwtException` throw → catch(Exception)에 걸려 EXPIRED 대신 INVALID 에러 반환.

**수정 1 — JWTUtil에 getClaims() 추가:**
```kotlin
fun getClaims(token: String): Claims {
    return Jwts.parser().verifyWith(secretKey).build()
        .parseSignedClaims(token).payload  // 만료 시 ExpiredJwtException throw
}
```

**수정 2 — catch 순서:**
```kotlin
try {
    val claims = jwtUtil.getClaims(accessToken)  // 1번만 파싱
    ...
} catch (e: ExpiredJwtException) {
    writeErrorResponse(..., ErrorCode.EXPIRED_ACCESS)
} catch (e: Exception) {
    writeErrorResponse(..., ErrorCode.INVALID_ACCESS)
}
```

**에러코드 의미:**
| 에러코드 | 상황 | 프론트 대응 |
|---------|------|------------|
| EXPIRED_ACCESS | access token 만료 | /reissue 호출 |
| INVALID_ACCESS | 위조/형식오류 | 재로그인 유도 |
| EXPIRED_REFRESH | refresh token 만료 | 재로그인 유도 |
| INVALID_REFRESH | 위조/형식오류 | 재로그인 유도 |

---

## 리뷰 기능

**API:** `POST /api/v1/reviews/{courseId}` (JWT 필수, multipart/form-data)

**중복 체크:** PENDING, APPROVED 상태 리뷰 있으면 409. REJECTED면 재작성 가능.

**이미지 저장:**
```kotlin
// 저장: src/main/resources/static/certifications/{uuid}_{파일명}
// DB: /certifications/{uuid}_{파일명}
// 접근: http://localhost:8080/certifications/{uuid}_{파일명}
```

**정렬 (ReviewSortType):** LATEST (기본) / HIGH_RATING / LOW_RATING

**Page vs Slice:** 무한스크롤은 COUNT 쿼리 없는 Slice 사용 (`hasNext: false`이면 중단).

**Validation:**
```kotlin
@NotNull @DecimalMin("1.0") @DecimalMax("5.0")  // rating
@NotBlank                                         // content
// -Xannotation-default-target=param-property 덕에 @field: 없이 바로 사용
```

### Swagger @RequestPart DTO 문제

Swagger UI가 `@RequestPart` DTO를 `string($binary)` (파일)로 렌더링 → `application/octet-stream`으로 전송.

**해결 — MultipartJackson2HttpMessageConverter:**
```kotlin
@Component
class MultipartJackson2HttpMessageConverter(objectMapper: ObjectMapper)
    : AbstractJackson2HttpMessageConverter(objectMapper, MediaType.APPLICATION_OCTET_STREAM) {
    override fun canWrite(clazz: Class<*>, mediaType: MediaType?) = false
    override fun canWrite(type: Type?, clazz: Class<*>, mediaType: MediaType?) = false
    override fun canWrite(mediaType: MediaType?) = false
}
```

---

## 강의 찜 기능

**atomic SQL (락 불필요):**
```kotlin
@Modifying
@Query("UPDATE Course c SET c.wishes = c.wishes + 1 WHERE c.id = :courseId")
fun incrementWishes(@Param("courseId") courseId: UUID)
```

**UniqueConstraint:** DB 레벨에서 동시 요청 중복 방지 (`course_id + member_id`).

**IN 쿼리로 찜 여부 일괄 조회 (N+1 방지):**
```kotlin
val wishedIds = courseWishRepository.findWishedCourseIds(courseIds, memberId).toSet()
coursePage.map { course -> CourseResponseDto(..., wished = wishedIds.contains(course.id)) }
```

**SecurityConfig:**
```kotlin
.requestMatchers(HttpMethod.POST, "/api/v1/courses/*/wish").authenticated()
// * = 단일 경로 변수, ** = 하위 경로 전체
```

---

## 관리자 기능

**임시 관리자 권한 부여 API (`PATCH /api/v1/members/{id}/role/admin`) — 프로덕션 배포 전 삭제 필요**

**pendingCount 항상 반환:** 어떤 탭을 보더라도 대기 중 리뷰 수 뱃지 표시용.

**Dirty Checking:** `@Transactional` + `var` 필드 → `save()` 없이 자동 UPDATE.

---

## 강의 조회 (QueryDSL)

**EXISTS Subquery (N:M 관계 필터링):**
```kotlin
val scc1 = QSubCategoryCourse("scc1")  // 카테고리 필터용 별칭
val scc2 = QSubCategoryCourse("scc2")  // 서브카테고리 필터용 별칭 (같은 별칭 쓰면 SQL 충돌)

builder.and(
    JPAExpressions.selectOne().from(scc1)
        .where(scc1.course.eq(qCourse), scc1.subCategory.category.slug.eq(category))
        .exists()
)
```

**JOIN + LIMIT 대신 EXISTS 쓰는 이유:** JOIN + 페이지네이션은 중복 행 발생.

**Kotlin 주의사항:**
```kotlin
qCourse.platform.englishName.`in`(platforms)  // in은 Kotlin 예약어 → 백틱
import org.springframework.transaction.annotation.Transactional  // readOnly 파라미터는 Spring 것만
coursePage.map { course: Course -> ... }  // 명시적 타입 지정 (타입 추론 실패)
```

### PageImpl 직렬화 경고 수정

```kotlin
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)  // CorsMvcConfig.kt에 추가
class CorsMvcConfig: WebMvcConfigurer { ... }
```

결과 구조가 `{ content: [...], page: { size, number, totalElements, totalPages } }`로 변경됨 (프론트 `data.page.totalElements` 방식 접근).

---

## 시간(Time) 처리 — UTC + Instant

**원칙:** 저장은 UTC(Instant), 표시는 프론트가 로컬 변환.

```kotlin
// 엔티티: LocalDateTime → Instant
import java.time.Instant
var createdAt: Instant? = null  // @CreatedDate도 Instant 자동 처리

// main()에 JVM 타임존 고정
TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
```

**application.yaml:**
```yaml
spring:
  jackson:
    time-zone: UTC
    serialization:
      write-dates-as-timestamps: false  # 숫자 대신 ISO-8601 (2024-02-03T10:00:00Z)
```

**포맷이 필요할 때:**
```kotlin
review.createdAt!!.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
// Instant.format() 없음 → atZone() 후 format()
```

---

## Elasticsearch + Spring AI

### 기술 스택

| 기능 | 방식 |
|------|------|
| 강의 검색 | 하이브리드 (BM25 키워드 + KNN 벡터 → RRF) |
| 강의 추천 | RAG (벡터 검색 + GPT-4o) |

**RRF (Reciprocal Rank Fusion):** 두 검색의 점수 체계가 달라 직접 더하기 어려움 → 순위 기반 합산.

### 의존성

```kotlin
implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
implementation("org.springframework.ai:spring-ai-elasticsearch-store-spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

repositories {
    maven { url = uri("https://repo.spring.io/milestone") }
}
dependencyManagement {
    imports { mavenBom("org.springframework.ai:spring-ai-bom:1.0.0-M6") }
    dependencies {
        dependency("io.swagger.core.v3:swagger-annotations-jakarta:2.2.30")  // Swagger 충돌 방지
    }
}
```

### 인덱스 설계

```kotlin
// @PostConstruct로 커스텀 인덱스 생성 (Spring AI 자동 생성 비활성화: initialize-schema: false)
.properties("title") { p ->
    p.text { t ->
        t.fields("korean") { f -> f.text { it.analyzer("nori") } }
         .fields("english") { f -> f.text { it.analyzer("standard") } }
    }
}
.properties("embedding") { p -> p.denseVector { d -> d.dims(512).index(true).similarity("dot_product") } }
```

**dot_product 이유:** OpenAI 임베딩은 정규화 벡터 → dot_product = cosine, 더 빠름.

**임베딩 텍스트:**
```kotlin
"Title: $title, Platform: $platform, Teacher: $teacher, Category: $category, SubCategory: $subCategory"
// rating, wishes는 임베딩에 넣으면 노이즈 → 랭킹 단계에서 function_score로 처리
```

**데이터 전략:** ES에는 검색 필드만 (id, platform, title, teacher, embedding) → id로 PostgreSQL에서 상세 조회.

### RAG 구현

```kotlin
@Service
class CourseRecommendService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore
) {
    fun recommend(query: String): String {
        return chatClient.prompt()
            .system("당신은 강의 추천 전문가입니다. 한국어로 답변해주세요.")
            .user(query)
            .advisors(QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults().withTopK(10)))
            .call().content()
    }
}
```

### application.yaml

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-large
          dimensions: 512
    vectorstore:
      elasticsearch:
        index-name: courses
        dimensions: 512
        initialize-schema: false
  elasticsearch:
    uris: http://localhost:9200
  data:
    elasticsearch:
      repositories:
        enabled: false  # JPA 레포지토리와 충돌 방지
```

### Spring AI + Swagger 충돌 버그

```
NoSuchMethodError: 'java.lang.Class[] io.swagger.v3.oas.annotations.Parameter.validationGroups()'
```

원인: Spring AI BOM이 구버전 swagger-annotations를 끌어옴. 해결: `dependencyManagement`에서 버전 강제 고정.

---

## GlobalExceptionHandler 개선

```kotlin
// 브라우저 자동 요청(favicon.ico 등)이 ERROR 로그 남기는 것 방지
@ExceptionHandler(NoResourceFoundException::class)
fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<Unit> {
    logger.debug("No resource found: ${e.message}")
    return ResponseEntity.notFound().build()
}

// catch 순서: 구체적 → 넓은 순
// BusinessException → MethodArgumentNotValidException → NoResourceFoundException → Exception
```

---

## 에러 코드

| 코드 | 상태 | 메시지 |
|------|------|--------|
| AUTH_401_UNAUTHORIZED | 401 | 로그인이 필요합니다 |
| AUTH_401_EXPIRED_ACCESS | 401 | 로그인이 만료되었습니다 |
| AUTH_401_INVALID_ACCESS | 401 | 유효하지 않은 토큰입니다 |
| AUTH_401_EXPIRED_REFRESH | 401 | Refresh token 만료 |
| AUTH_401_INVALID_REFRESH | 401 | 유효하지 않은 refresh token |
| AUTH_403_FORBIDDEN | 403 | 권한이 없습니다 |
| COMMON_404_NOT_FOUND | 404 | 대상을 찾을 수 없습니다 |
| COMMON_409_CONFLICT | 409 | 이미 존재합니다 |

---

## Spring Boot 버전 관련

**Spring Boot 3.4.1 선택 이유:** Spring Boot 4.0 + Spring Batch 6.0에서는 REST API로 Job 실행 제한 (`JobOperator.start()` deprecated). 모놀리식 구조 유지를 위해 다운그레이드.

**주요 변경사항 (4.0 → 3.4.1):**
- Jackson: `tools.jackson` → `com.fasterxml.jackson`
- Spring Batch: 6.x → 5.x (import 경로, chunk 문법 롤백)
- Job 실행: `jobOperator.start()` → `jobLauncher.run(job, params)`
- 테스트: `spring-boot-starter-webmvc-test` → `spring-boot-starter-test`
