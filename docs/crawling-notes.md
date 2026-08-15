# 크롤링 개발 노트

## Spring Batch 구조

```
open() - 1번 호출 (초기화, 크롤링)
    ↓
read() - N번 호출 (하나씩 반환, null이면 종료)
    ↓
Processor - 중복 체크
    ↓
Writer - DB 저장
    ↓
close() - 1번 호출 (리소스 정리)
```

**chunk(5) 의미:** read() 5번 → Processor 5번 → Writer 1번 (5개 묶어서 저장). 트랜잭션 단위 = chunk 단위.

**chunk 크기 가이드:**
| 상황 | chunk 크기 |
|------|-----------|
| 노마드코더 (강의 ~50개) | 5 |
| 인프런 (강의 수만 개) | 20 |
| 크롤링 일반 권장 | 10~30 |

### Spring Batch 재시작 (ExecutionContext)

```kotlin
companion object {
    private const val READ_COUNT_KEY = "nomadcoders.read.count"
}

override fun open(executionContext: ExecutionContext) {
    currentIndex = executionContext.getInt(READ_COUNT_KEY, 0)
}

override fun update(executionContext: ExecutionContext) {
    executionContext.putInt(READ_COUNT_KEY, currentIndex)  // chunk 완료 시마다 저장
}
```

DB 테이블 `BATCH_STEP_EXECUTION_CONTEXT`에 상태 저장. 같은 jobId로 재실행하면 이어서 처리.

### Spring Batch 예외 처리 규칙

```kotlin
.faultTolerant()
.retryLimit(3)
.retry(ItemStreamException::class.java)
.skipLimit(200)
.skip(Exception::class.java)  // retry 등록 예외는 반드시 skip에도 포함 (상위 클래스로)
```

`retry` 예외가 `skip` 목록에 없으면 "Non-skippable exception in recoverer" RetryException 발생.

### @StepScope 주의

```kotlin
// Kotlin 클래스는 기본 final → CGLIB 프록시 불가 → open 필요
open class NomadcodersReader(...) : ItemStreamReader<Course>
```

`kotlin-plugin.spring`은 @Service, @Component는 자동 open하지만 @StepScope는 인식 못함.

### JobParameters → @StepScope로 전달

```kotlin
@Bean
@StepScope
fun inflearnReader(
    @Value("#{jobParameters['maxCategories'] ?: 0}") maxCategories: Long
): ItemReader<InflearnCrawlingDto> {
    return InflearnReader(..., maxCategories = maxCategories.toInt())
}
```

---

## 비동기 크롤링 (@Async)

크롤링은 5분~30분 소요 → 동기 방식이면 504 타임아웃 발생.

```kotlin
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {
    @Bean("crawlingExecutor")
    fun crawlingExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 10
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        return executor
    }
}

@Async("crawlingExecutor")
fun runCrawlingJobAsync(jobName: String, jobId: String) { ... }
```

**사용 흐름:**
```bash
POST /api/v1/crawling/courses?jobName=inflearnCrawlingJob  → 즉시 응답 (jobId 반환)
GET /api/v1/crawling/status/{jobId}                        → 상태 확인 (STARTED/COMPLETED/FAILED)
```

---

## 노마드코더 크롤링

**방식:** Next.js `<script id="__NEXT_DATA__">` 에서 JSON 직접 파싱 (DOM 파싱 불필요, 무한스크롤 무시).

```javascript
// 브라우저 콘솔에서 확인
JSON.parse(document.getElementById('__NEXT_DATA__').textContent)
```

**페이지 로딩 대기:**
```kotlin
val wait = WebDriverWait(driver, Duration.ofSeconds(10))
wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("script#__NEXT_DATA__")))
```

**버퍼 방식 (read()에서 하나씩 생성):**
```kotlin
override fun open(...) {
    courseKeys = apolloState.fieldNames().filter { it.startsWith("course:") }.toList()
}
override fun read(): Course? {
    if (currentIndex >= courseKeys.size) return null
    val node = apolloState.get(courseKeys[currentIndex++])
    return Course(...)
}
```

---

## 인프런 크롤링

**특징:** CSR + 2단계 Lazy Load (강의 20개 → 지식공유자 섹션 → 강의 20개). scroll 없이는 20개만 파싱.

**셀렉터 (Mantine UI 해시 기반 - 변경 가능):**
```kotlin
private const val SELECTOR_COURSE_LIST = "ul.mantine-1avyp1d"
private const val SELECTOR_TITLE = "p.mantine-fcy4ne"
private const val SELECTOR_TEACHER = "p.mantine-aiouth"
```

**Lazy load 대응:**
```kotlin
// JS textContent (off-screen 요소에서 .text는 빈 string 반환)
val title = (driver as JavascriptExecutor)
    .executeScript("return arguments[0].textContent", titleEl) as? String ?: ""

// 링크 20개 초과될 때까지 반복 scroll
val courseSelector = "$SELECTOR_COURSE_LIST $SELECTOR_COURSE_ITEM a[href*='/course/']"
wait.until { d ->
    val count = (js.executeScript("""return document.querySelectorAll("$courseSelector").length;""") as Number).toLong()
    if (count <= 20L) js.executeScript("window.scrollTo(0, document.body.scrollHeight)")
    count > 20L
}
```

**JS quoting 주의:** JS 문자열 outer delimiter는 double quote 사용 (courseSelector 안에 single quote `[href*='/course/']` 있어서).

**JS return 타입:** `as Long` ClassCastException 발생 → `(js.executeScript("...") as Number).toLong()` 사용.

**재시작 키:** `inflearn.category.index`, `inflearn.subcategory.index`, `inflearn.page.number`

**URL 패턴:**
```kotlin
"$BASE_URL/${category.slug}/${subCategory.slug}?types=ONLINE&page_number=$currentPage"
```

---

## 코드잇 크롤링

**특징:** CSR + scroll lazy load. CSS 모듈 해시 기반 클래스명 → 부분 매칭 사용.

**셀렉터 (부분 매칭):**
```kotlin
private const val SELECTOR_COURSE_GRID = "[class*='TopicList_grid']"
private const val SELECTOR_COURSE_CARD = "a[class*='TopicCommonCard_body']"
private const val SELECTOR_COURSE_TITLE = "[class*='TopicCommonCard_title']"
```

**로드맵 섹션 스킵 (버그 수정):** 페이지 상단 로드맵 그리드와 강의 그리드가 동일 클래스 → `lastOrNull()` 사용.
```kotlin
val grids = driver.findElements(By.cssSelector(SELECTOR_COURSE_GRID))
val mainGrid = grids.lastOrNull()  // 마지막 = 실제 강의 그리드
```

**scrollHeight 안정화 감지:**
```kotlin
private fun scrollToBottom() {
    var stableCount = 0
    repeat(5) {
        jsExec.executeScript("window.scrollTo(0, document.body.scrollHeight)")
        Thread.sleep(800)
        // scrollHeight 이전과 같으면 stableCount++, 2회 연속 → 완료
    }
}
```

**URL 패턴:**
```kotlin
// default 서브카테고리 (slug == category slug)
"https://www.codeit.kr/explore?categorySlug=basic&page=1"
// 실제 서브카테고리
"https://www.codeit.kr/explore?categorySlug=data&page=1&subCategory=DATA_ANALYSIS"
```

---

## 코딩애플 크롤링

**특징:** SSR, 카테고리 없음, Course 직접 반환 (노마드코더 패턴).

**URL:** `https://codingapple.com/all-courses/`

**셀렉터:**
```kotlin
private const val SELECTOR_COURSE_LIST = "ul#course-list"
private const val SELECTOR_COURSE_ITEM = "li.course_single_item"
private const val SELECTOR_COURSE_LINK = "div.item-avatar a"
private const val SELECTOR_TITLE_LINK = "div.item-title a"
```

**슬러그 추출:** `courseUrl.trimEnd('/').substringAfterLast("/")`

---

## 패스트캠퍼스 크롤링

**특징:** 무한 스크롤 (infinite scroll), CSS 모듈 해시, 팝업 방지.

**셀렉터 (이중 부분 매칭):**
```kotlin
private const val SELECTOR_COURSE_LIST = "[class*='InfinityCourse'][class*='infinityCourse']"
private const val SELECTOR_COURSE_CARD = "[class*='CourseCard'][class*='courseCardContainer']"
private const val SELECTOR_TITLE = "span[class*='CourseCard'][class*='courseCardTitle']"
```

**무한 스크롤 안정화 감지:**
```kotlin
private fun scrollUntilAllLoaded() {
    var previousCount = 0
    var stableCount = 0
    repeat(50) {
        jsExec.executeScript("window.scrollTo(0, document.body.scrollHeight)")
        Thread.sleep(1500)
        val currentCount = driver.findElements(By.cssSelector(SELECTOR_COURSE_CARD)).size
        if (currentCount == previousCount) {
            if (++stableCount >= 2) return  // 2회 연속 변화 없음 = 완료
        } else { stableCount = 0; previousCount = currentCount }
    }
}
```

**팝업 방지:**
```kotlin
// 1. Chrome 옵션 (--disable-popup-blocking, --disable-notifications)
// 2. ESC 키 전송
driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE)
```

**URL 패턴:** `https://fastcampus.co.kr/{subCategory.slug}`

---

## ChromeOptions 공통 설정

```kotlin
val options = ChromeOptions().apply {
    addArguments("--headless=new")
    addArguments("--no-sandbox")
    addArguments("--disable-gpu")
    addArguments("--window-size=1920,1080")
    addArguments("--disable-blink-features=AutomationControlled")
    addArguments("user-agent=Mozilla/5.0 ...")
}
```

---

## 플랫폼별 패턴 비교

| 플랫폼 | 렌더링 | 카테고리 | DTO | Writer 패턴 |
|--------|--------|---------|-----|-------------|
| 노마드코더 | SSR + __NEXT_DATA__ | 없음 | Course 직접 | Course 저장 |
| 코딩애플 | SSR | 없음 | Course 직접 | Course 저장 |
| 인프런 | CSR + 2단계 lazy | 2-depth | CrawlingCourseDto | Course + SubCategoryCourse |
| 코드잇 | CSR + scroll | 2-depth | CrawlingCourseDto | Course + SubCategoryCourse |
| 패스트캠퍼스 | 무한 스크롤 | 2-depth | CrawlingCourseDto | Course + SubCategoryCourse |

---

## 카테고리 JSON 구조 (인프런/코드잇/패스트캠퍼스 공통)

```json
[
  {
    "slug": "artificial-intelligence",
    "title": "AI 기술",
    "children": [
      { "slug": "ai-agent-development", "title": "AI에이전트 개발" }
    ]
  }
]
```

`createCategories(platform, jsonPath)` 메서드가 3개 플랫폼 모두 재사용.

---

## 인프런 셀렉터 변경 감지 로깅

```kotlin
// 첫 실행 시 셀렉터 검증
if (categoryIndex == 0 && subCategoryIndex == 0 && currentPage == 1) validateSelectors()

// 파싱 실패율 50% 이상이면 경고
if (allCourses.isNotEmpty() && parseFailCount > allCourses.size / 2)
    log.warn("파싱 실패율이 높음. 셀렉터 확인 필요")
```
