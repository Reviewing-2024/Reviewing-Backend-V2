package my.reviewing.reviewing_V2.crawling.crawlingBatch.codeit

import my.reviewing.reviewing_V2.crawling.dto.CrawlingCourseDto
import my.reviewing.reviewing_V2.crawling.entity.Category
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.entity.SubCategory
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader
import java.time.Duration

/**
 * 코드잇 강의 크롤링 Reader
 *
 * - CrawlingCourseDto 재사용 (teacher = "", thumbnailImage/Video = null)
 * - 2-depth 서브카테고리 지원
 *   - default 서브카테고리 (slug == category slug): ?categorySlug=basic&page=1
 *   - 실제 서브카테고리: ?categorySlug=data&page=1&subCategory=DATA_ANALYSIS
 * - 재시작 지원 (ExecutionContext)
 * - CSR 대응: scrollToBottom()으로 페이지네이션 포함 전체 렌더링 대기
 *
 * @param maxCategories 최대 카테고리 수 (0 = 무제한, 테스트용)
 * @param maxSubCategoriesPerCategory 카테고리당 최대 서브카테고리 수 (0 = 무제한)
 * @param maxPagesPerSubCategory 서브카테고리당 최대 페이지 수 (0 = 무제한)
 */
open class CodeitReader(
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val maxCategories: Int = 0,
    private val maxSubCategoriesPerCategory: Int = 0,
    private val maxPagesPerSubCategory: Int = 0
) : ItemStreamReader<CrawlingCourseDto> {

    private val log = LoggerFactory.getLogger(CodeitReader::class.java)

    companion object {
        private const val CATEGORY_INDEX_KEY = "codeit.category.index"
        private const val SUBCATEGORY_INDEX_KEY = "codeit.subcategory.index"
        private const val PAGE_NUMBER_KEY = "codeit.page.number"
        private const val BASE_URL = "https://www.codeit.kr/explore"

        // 셀렉터 상수
        // CSS 모듈 해시(ex: TopicList_grid__7bZ8U)가 배포마다 바뀔 수 있으므로
        // 부분 매칭([class*=]) 사용 → 해시 변경에도 동작
        private const val SELECTOR_COURSE_GRID = "[class*='TopicList'][class*='grid']"
        private const val SELECTOR_COURSE_CARD = "[class*='TopicCommonCard'][class*='container']"
        private const val SELECTOR_COURSE_TITLE = "p[class*='TopicCommonCard'][class*='title']"
        private const val SELECTOR_PAGINATION_PAGE = "button[class*='Pagination'][class*='page']"
    }

    private lateinit var driver: WebDriver
    private lateinit var platform: Platform

    // 카테고리 순회
    private lateinit var categories: List<Category>
    private var categoryIndex = 0
    private var maxCategoryIndex = Int.MAX_VALUE

    // 서브카테고리 순회
    private var subCategories: List<SubCategory> = emptyList()
    private var subCategoryIndex = 0
    private var maxSubCategoryIndex = Int.MAX_VALUE

    // 페이지네이션
    private var currentPage = 1
    private var lastPage = 1

    // 현재 페이지 강의 버퍼
    private var courseBuffer: MutableList<CrawlingCourseDto> = mutableListOf()

    override fun open(executionContext: ExecutionContext) {
        // 1. 이전 상태 복원 (재시작)
        categoryIndex = executionContext.getInt(CATEGORY_INDEX_KEY, 0)
        subCategoryIndex = executionContext.getInt(SUBCATEGORY_INDEX_KEY, 0)
        currentPage = executionContext.getInt(PAGE_NUMBER_KEY, 1)

        if (categoryIndex > 0 || subCategoryIndex > 0 || currentPage > 1) {
            log.info("재시작 감지: categoryIndex={}, subCategoryIndex={}, page={}",
                categoryIndex, subCategoryIndex, currentPage)
        }

        // 2. Platform 조회
        platform = platformRepository.findByKoreanName("코드잇")
            ?: throw ItemStreamException("코드잇 플랫폼을 찾을 수 없습니다. 먼저 플랫폼을 생성해주세요.")

        // 3. 카테고리 목록 조회
        categories = categoryRepository.findByPlatform(platform)
        if (categories.isEmpty()) {
            throw ItemStreamException("코드잇 카테고리가 없습니다. 먼저 카테고리를 생성해주세요.")
        }

        maxCategoryIndex = if (maxCategories > 0) minOf(maxCategories, categories.size) else categories.size

        if (maxCategories > 0 || maxSubCategoriesPerCategory > 0 || maxPagesPerSubCategory > 0) {
            log.info("🧪 테스트 모드 - 제한 설정: 카테고리={}개, 서브카테고리={}개/카테고리, 페이지={}개/서브카테고리",
                if (maxCategories > 0) maxCategories else "무제한",
                if (maxSubCategoriesPerCategory > 0) maxSubCategoriesPerCategory else "무제한",
                if (maxPagesPerSubCategory > 0) maxPagesPerSubCategory else "무제한")
        }

        log.info("총 {}개 카테고리 발견 (크롤링 대상: {}개)", categories.size, maxCategoryIndex)

        // 4. ChromeDriver 초기화
        val options = ChromeOptions().apply {
            addArguments("--headless=new")
            addArguments("--no-sandbox")
            addArguments("--disable-gpu")
            addArguments("--disable-popup-blocking")
            addArguments("--disable-notifications")
            addArguments("--disable-blink-features=AutomationControlled")
            addArguments("--window-size=1920,1080")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        driver = ChromeDriver(options)
        log.info("ChromeDriver 초기화 완료")

        // 5. 재시작 시 해당 카테고리의 서브카테고리 로드
        if (categoryIndex < categories.size) {
            loadSubCategories(categories[categoryIndex])
        }
    }

    override fun read(): CrawlingCourseDto? {
        // 1. 버퍼에 있으면 반환
        if (courseBuffer.isNotEmpty()) return courseBuffer.removeAt(0)

        // 2. 현재 페이지가 남아있으면 크롤링
        if (currentPage <= lastPage && subCategoryIndex < maxSubCategoryIndex) {
            crawlCurrentPage()
            if (courseBuffer.isNotEmpty()) return courseBuffer.removeAt(0)
        }

        // 3. 다음 서브카테고리/카테고리로 이동
        while (subCategoryIndex < maxSubCategoryIndex - 1 || categoryIndex < maxCategoryIndex - 1) {
            if (subCategoryIndex < maxSubCategoryIndex - 1) {
                subCategoryIndex++
                currentPage = 1
                findLastPage()
                crawlCurrentPage()
                if (courseBuffer.isNotEmpty()) return courseBuffer.removeAt(0)
            } else if (categoryIndex < maxCategoryIndex - 1) {
                categoryIndex++
                subCategoryIndex = 0
                currentPage = 1
                loadSubCategories(categories[categoryIndex])
                if (subCategories.isNotEmpty() && maxSubCategoryIndex > 0) {
                    crawlCurrentPage()
                    if (courseBuffer.isNotEmpty()) return courseBuffer.removeAt(0)
                }
            }
        }

        log.info("코드잇 크롤링 완료 (카테고리: {}/{}, 서브카테고리: {}/{})",
            categoryIndex + 1, maxCategoryIndex,
            subCategoryIndex + 1, maxSubCategoryIndex)
        return null
    }

    override fun update(executionContext: ExecutionContext) {
        executionContext.putInt(CATEGORY_INDEX_KEY, categoryIndex)
        executionContext.putInt(SUBCATEGORY_INDEX_KEY, subCategoryIndex)
        executionContext.putInt(PAGE_NUMBER_KEY, currentPage)
    }

    override fun close() {
        if (::driver.isInitialized) {
            try {
                driver.quit()
                log.info("ChromeDriver 종료")
            } catch (e: Exception) {
                log.warn("ChromeDriver 종료 중 에러 (무시됨): {}", e.message)
            }
        }
    }

    private fun loadSubCategories(category: Category) {
        val allSubCategories = subCategoryRepository.findByCategory(category)
        maxSubCategoryIndex = if (maxSubCategoriesPerCategory > 0) {
            minOf(maxSubCategoriesPerCategory, allSubCategories.size)
        } else {
            allSubCategories.size
        }
        subCategories = allSubCategories
        log.info("카테고리 [{}] - {}개 서브카테고리 (크롤링 대상: {}개)",
            category.name, allSubCategories.size, maxSubCategoryIndex)

        if (subCategories.isNotEmpty() && subCategoryIndex < maxSubCategoryIndex) {
            findLastPage()
        }
    }

    /**
     * URL 생성
     *
     * codeit_categories.json에서 slug가 상위 카테고리와 동일한 경우 (basic/basic, ai/ai 등)
     * → subCategory 파라미터 없이 categorySlug만 사용 (default 서브카테고리)
     *
     * slug가 다른 경우 (data/DATA_ANALYSIS 등)
     * → subCategory 파라미터 추가
     */
    private fun buildUrl(category: Category, subCategory: SubCategory, page: Int): String {
        val isDefault = subCategory.slug == category.slug
        return if (isDefault) {
            "$BASE_URL?categorySlug=${category.slug}&page=$page"
        } else {
            "$BASE_URL?categorySlug=${category.slug}&page=$page&subCategory=${subCategory.slug}"
        }
    }

    private fun findLastPage() {
        if (subCategories.isEmpty() || subCategoryIndex >= subCategories.size) {
            lastPage = 1
            return
        }

        val category = categories[categoryIndex]
        val subCategory = subCategories[subCategoryIndex]
        val url = buildUrl(category, subCategory, 1)
        log.info("마지막 페이지 조회: {}", url)

        try {
            driver.get(url)
            waitForCourses()
            scrollToBottom()

            val pageButtons = driver.findElements(By.cssSelector(SELECTOR_PAGINATION_PAGE))
            val foundLastPage = if (pageButtons.isEmpty()) {
                1
            } else {
                pageButtons.mapNotNull { it.text.trim().toIntOrNull() }.maxOrNull() ?: 1
            }

            lastPage = if (maxPagesPerSubCategory > 0) {
                minOf(maxPagesPerSubCategory, foundLastPage)
            } else {
                foundLastPage
            }

            if (maxPagesPerSubCategory > 0 && foundLastPage > maxPagesPerSubCategory) {
                log.info("서브카테고리 [{}] - 총 {}페이지 (제한: {}페이지만 크롤링)",
                    subCategory.name, foundLastPage, lastPage)
            } else {
                log.info("서브카테고리 [{}] - 총 {}페이지", subCategory.name, lastPage)
            }
        } catch (e: Exception) {
            log.warn("마지막 페이지 조회 실패, 1페이지만 크롤링: {}", e.message)
            lastPage = 1
        }
    }

    private fun crawlCurrentPage() {
        if (subCategories.isEmpty() || subCategoryIndex >= subCategories.size) return

        val category = categories[categoryIndex]
        val subCategory = subCategories[subCategoryIndex]
        val url = buildUrl(category, subCategory, currentPage)
        log.info("크롤링: {} (페이지 {}/{})", url, currentPage, lastPage)

        try {
            driver.get(url)
            waitForCourses()
            scrollToBottom()

            // 페이지 상단의 "목표 달성 로드맵" 섹션 제외 → 가장 마지막 그리드(본 강의 목록)만 사용
            val grids = driver.findElements(By.cssSelector(SELECTOR_COURSE_GRID))
            val mainGrid = grids.lastOrNull()
            val courseCards = mainGrid?.findElements(By.cssSelector(SELECTOR_COURSE_CARD)) ?: emptyList()
            log.info("총 {}개 강의 카드 발견 (그리드 {}개 중 마지막 사용)", courseCards.size, grids.size)

            if (courseCards.isEmpty()) {
                log.warn("⚠️ [셀렉터 변경 감지] 강의 카드를 찾을 수 없음. 셀렉터: {}", SELECTOR_COURSE_CARD)
            }

            var parseFailCount = 0
            for (card in courseCards) {
                try {
                    val dto = parseCourseCard(card, subCategory)
                    if (dto != null) {
                        courseBuffer.add(dto)
                    } else {
                        parseFailCount++
                    }
                } catch (e: Exception) {
                    parseFailCount++
                    log.debug("강의 파싱 실패: {}", e.message)
                }
            }

            if (courseCards.isNotEmpty() && parseFailCount > courseCards.size / 2) {
                log.warn("⚠️ [셀렉터 변경 감지] 파싱 실패율이 높음: {}/{} 실패. 셀렉터 확인 필요",
                    parseFailCount, courseCards.size)
            }

            log.info("{}개 강의 크롤링 완료 (실패: {}개)", courseBuffer.size, parseFailCount)
            currentPage++

        } catch (e: Exception) {
            log.error("페이지 크롤링 실패: {}", e.message)
            currentPage++
        }
    }

    private fun parseCourseCard(card: WebElement, subCategory: SubCategory): CrawlingCourseDto? {
        // SELECTOR_COURSE_CARD가 <div>이므로 내부의 <a href*='/topics/'> 찾기
        val link = try {
            card.findElement(By.cssSelector("a[href*='/topics/']"))
        } catch (_: Exception) { return null }

        val courseUrl = link.getAttribute("href") ?: return null
        val courseSlug = courseUrl.substringAfterLast("/").substringBefore("?")

        val title = try {
            val titleEl = card.findElement(By.cssSelector(SELECTOR_COURSE_TITLE))
            ((driver as JavascriptExecutor)
                .executeScript("return arguments[0].textContent", titleEl) as? String)
                ?.trim() ?: return null
        } catch (_: Exception) {
            log.debug("⚠️ [셀렉터 변경 감지] 제목 요소 없음. 셀렉터: {}", SELECTOR_COURSE_TITLE)
            return null
        }

        if (title.isEmpty()) return null

        return CrawlingCourseDto(
            platform = platform,
            subCategory = subCategory,
            title = title,
            courseUrl = courseUrl,
            courseSlug = courseSlug,
            thumbnailImage = null,
            thumbnailVideo = null,
            teacher = "codeit"
        )
    }

    /**
     * 강의 그리드가 렌더링될 때까지 대기 (CSR)
     * 1. document.readyState == complete
     * 2. 강의 그리드 DOM 요소 등장
     */
    private fun waitForCourses() {
        val wait = WebDriverWait(driver, Duration.ofSeconds(30))
        wait.until { d ->
            (d as JavascriptExecutor).executeScript("return document.readyState") == "complete"
        }
        try {
            wait.until { d ->
                d.findElements(By.cssSelector(SELECTOR_COURSE_GRID)).isNotEmpty()
            }
        } catch (_: Exception) {
            log.warn("⚠️ 강의 그리드 대기 타임아웃. 셀렉터: {}", SELECTOR_COURSE_GRID)
        }
    }

    /**
     * 페이지 최하단까지 반복 스크롤
     * - 코드잇은 CSR + lazy load: 스크롤 후 페이지네이션 등 추가 요소 렌더링
     * - scrollHeight가 더 이상 증가하지 않으면 중단 (최대 5회)
     */
    private fun scrollToBottom() {
        val jsExec = driver as JavascriptExecutor
        var previousHeight = 0L
        repeat(5) {
            jsExec.executeScript("window.scrollTo(0, document.body.scrollHeight)")
            Thread.sleep(800)
            val currentHeight = (jsExec.executeScript("return document.body.scrollHeight") as Number).toLong()
            if (currentHeight == previousHeight) return
            previousHeight = currentHeight
        }
    }
}