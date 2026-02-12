package my.reviewing.reviewing_V2.crawling.crawlingBatch.fastcampus

import my.reviewing.reviewing_V2.crawling.dto.CrawlingCourseDto
import my.reviewing.reviewing_V2.crawling.entity.Category
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.entity.SubCategory
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
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
 * 패스트캠퍼스 강의 크롤링 Reader
 *
 * - 2-depth 카테고리 (Category → SubCategory)
 * - 무한 스크롤: 강의 수가 2회 연속 안정될 때까지 반복 스크롤
 * - 팝업 방지: Chrome 옵션(브라우저 팝업) + 페이지 로드 후 ESC(모달 팝업)
 * - 재시작 지원 (ExecutionContext)
 * - URL: https://fastcampus.co.kr/{subCategory.slug}
 *
 * @param maxCategories 최대 카테고리 수 (0 = 무제한)
 * @param maxSubCategoriesPerCategory 카테고리당 최대 서브카테고리 수 (0 = 무제한)
 */
class FastcampusReader(
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val maxCategories: Int = 0,
    private val maxSubCategoriesPerCategory: Int = 0
) : ItemStreamReader<CrawlingCourseDto> {

    private val log = LoggerFactory.getLogger(FastcampusReader::class.java)

    companion object {
        private const val CATEGORY_INDEX_KEY = "fastcampus.category.index"
        private const val SUBCATEGORY_INDEX_KEY = "fastcampus.subcategory.index"
        private const val BASE_URL = "https://fastcampus.co.kr"

        // CSS 모듈 해시(ex: CourseCard-module-scss-module__6SvHWG__title)가 배포마다 바뀔 수 있으므로
        // 부분 매칭([class*=]) 사용
        private const val SELECTOR_COURSE_LIST  = "[class*='InfinityCourse'][class*='infinityCourse']"
        private const val SELECTOR_COURSE_CARD  = "[class*='CourseCard'][class*='courseCardContainer']"
        private const val SELECTOR_IMAGE_LINK   = "a[class*='CourseCard'][class*='courseCardImageWrapper']"
        private const val SELECTOR_THUMBNAIL    = "a[class*='CourseCard'][class*='courseCardImageWrapper'] img"
        private const val SELECTOR_TITLE        = "span[class*='CourseCard'][class*='courseCardTitle']"
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

    // 현재 서브카테고리 강의 버퍼
    private var courseBuffer: MutableList<CrawlingCourseDto> = mutableListOf()

    override fun open(executionContext: ExecutionContext) {
        categoryIndex = executionContext.getInt(CATEGORY_INDEX_KEY, 0)
        subCategoryIndex = executionContext.getInt(SUBCATEGORY_INDEX_KEY, 0)

        if (categoryIndex > 0 || subCategoryIndex > 0) {
            log.info("재시작 감지: categoryIndex={}, subCategoryIndex={}", categoryIndex, subCategoryIndex)
        }

        platform = platformRepository.findByKoreanName("패스트캠퍼스")
            ?: throw ItemStreamException("패스트캠퍼스 플랫폼을 찾을 수 없습니다. 먼저 플랫폼을 생성해주세요.")

        categories = categoryRepository.findByPlatform(platform)
        if (categories.isEmpty()) {
            throw ItemStreamException("패스트캠퍼스 카테고리가 없습니다. 먼저 카테고리를 생성해주세요.")
        }

        maxCategoryIndex = if (maxCategories > 0) minOf(maxCategories, categories.size) else categories.size

        if (maxCategories > 0 || maxSubCategoriesPerCategory > 0) {
            log.info("🧪 테스트 모드 - 제한 설정: 카테고리={}개, 서브카테고리={}개/카테고리",
                if (maxCategories > 0) maxCategories else "무제한",
                if (maxSubCategoriesPerCategory > 0) maxSubCategoriesPerCategory else "무제한")
        }

        log.info("총 {}개 카테고리 발견 (크롤링 대상: {}개)", categories.size, maxCategoryIndex)

        val options = ChromeOptions().apply {
            addArguments("--headless=new")
            addArguments("--no-sandbox")
            addArguments("--disable-gpu")
            addArguments("--disable-popup-blocking")   // 브라우저 팝업 차단
            addArguments("--disable-notifications")    // 알림 팝업 차단
            addArguments("--disable-default-apps")
            addArguments("--disable-extensions")
            addArguments("--disable-blink-features=AutomationControlled")
            addArguments("--window-size=1920,1080")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        driver = ChromeDriver(options)
        log.info("ChromeDriver 초기화 완료")

        if (categoryIndex < categories.size) {
            loadSubCategories(categories[categoryIndex])
        }
    }

    override fun read(): CrawlingCourseDto? {
        if (courseBuffer.isNotEmpty()) return courseBuffer.removeAt(0)

        while (categoryIndex < maxCategoryIndex) {
            if (subCategories.isEmpty()) {
                categoryIndex++
                subCategoryIndex = 0
                if (categoryIndex < maxCategoryIndex) loadSubCategories(categories[categoryIndex])
                continue
            }

            if (subCategoryIndex < maxSubCategoryIndex) {
                val subCategory = subCategories[subCategoryIndex]
                crawlSubCategory(categories[categoryIndex], subCategory)
                subCategoryIndex++
                if (courseBuffer.isNotEmpty()) return courseBuffer.removeAt(0)
            } else {
                categoryIndex++
                subCategoryIndex = 0
                if (categoryIndex < maxCategoryIndex) loadSubCategories(categories[categoryIndex])
            }
        }

        log.info("패스트캠퍼스 크롤링 완료")
        return null
    }

    override fun update(executionContext: ExecutionContext) {
        executionContext.putInt(CATEGORY_INDEX_KEY, categoryIndex)
        executionContext.putInt(SUBCATEGORY_INDEX_KEY, subCategoryIndex)
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
    }

    private fun crawlSubCategory(category: Category, subCategory: SubCategory) {
        val url = "$BASE_URL/${subCategory.slug}"
        log.info("크롤링: {} (카테고리: {}, 서브카테고리: {})", url, category.name, subCategory.name)

        try {
            driver.get(url)
            waitForCourseList()
            closePopupIfPresent()
            scrollUntilAllLoaded()

            val courseCards = driver.findElements(By.cssSelector(SELECTOR_COURSE_CARD))
            log.info("총 {}개 강의 카드 발견", courseCards.size)

            if (courseCards.isEmpty()) {
                log.warn("⚠️ [셀렉터 변경 감지] 강의 카드를 찾을 수 없음. 셀렉터: {}", SELECTOR_COURSE_CARD)
                return
            }

            var parseFailCount = 0
            for (card in courseCards) {
                try {
                    val dto = parseCourseCard(card, subCategory)
                    if (dto != null) courseBuffer.add(dto)
                    else parseFailCount++
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

        } catch (e: Exception) {
            log.error("서브카테고리 [{}] 크롤링 실패: {}", subCategory.name, e.message)
        }
    }

    private fun parseCourseCard(card: WebElement, subCategory: SubCategory): CrawlingCourseDto? {
        val imageLink = try {
            card.findElement(By.cssSelector(SELECTOR_IMAGE_LINK))
        } catch (_: Exception) { return null }

        val href = imageLink.getAttribute("href") ?: return null
        val courseUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
        val courseSlug = courseUrl.trimEnd('/').substringAfterLast("/")
        if (courseSlug.isEmpty()) return null

        val thumbnailImage = try {
            card.findElement(By.cssSelector(SELECTOR_THUMBNAIL)).getAttribute("src")
        } catch (_: Exception) {
            log.debug("⚠️ [셀렉터 변경 감지] 썸네일 없음. 셀렉터: {}", SELECTOR_THUMBNAIL)
            null
        }

        val title = try {
            val titleEl = card.findElement(By.cssSelector(SELECTOR_TITLE))
            ((driver as JavascriptExecutor)
                .executeScript("return arguments[0].textContent", titleEl) as? String)
                ?.trim() ?: return null
        } catch (_: Exception) {
            log.debug("⚠️ [셀렉터 변경 감지] 제목 없음. 셀렉터: {}", SELECTOR_TITLE)
            return null
        }

        if (title.isEmpty()) return null

        return CrawlingCourseDto(
            platform = platform,
            subCategory = subCategory,
            title = title,
            courseUrl = courseUrl,
            courseSlug = courseSlug,
            thumbnailImage = thumbnailImage,
            thumbnailVideo = null,
            teacher = "fastcampus"
        )
    }

    /**
     * 강의 목록이 렌더링될 때까지 대기 (CSR)
     */
    private fun waitForCourseList() {
        val wait = WebDriverWait(driver, Duration.ofSeconds(30))
        wait.until { d ->
            (d as JavascriptExecutor).executeScript("return document.readyState") == "complete"
        }
        try {
            wait.until { d ->
                d.findElements(By.cssSelector(SELECTOR_COURSE_LIST)).isNotEmpty()
            }
        } catch (_: Exception) {
            log.warn("⚠️ 강의 목록 대기 타임아웃. 셀렉터: {}", SELECTOR_COURSE_LIST)
        }
    }

    /**
     * 이벤트 팝업 닫기
     * - Chrome 옵션: 브라우저 레벨 팝업/알림 차단
     * - ESC 키: 사이트 모달 팝업 닫기 (1초 대기 후)
     */
    private fun closePopupIfPresent() {
        try {
            Thread.sleep(1000)
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE)
        } catch (_: Exception) { }
    }

    /**
     * 무한 스크롤 완료까지 반복 스크롤
     * - 1.5초 대기 후 강의 수 확인
     * - 2회 연속 변화 없으면 종료 (최대 50회)
     */
    private fun scrollUntilAllLoaded() {
        val jsExec = driver as JavascriptExecutor
        var previousCount = 0
        var stableCount = 0

        repeat(50) {
            jsExec.executeScript("window.scrollTo(0, document.body.scrollHeight)")
            Thread.sleep(1500)

            val currentCount = driver.findElements(By.cssSelector(SELECTOR_COURSE_CARD)).size
            log.debug("스크롤 중: {}개 강의 감지", currentCount)

            if (currentCount == previousCount) {
                stableCount++
                if (stableCount >= 2) return
            } else {
                stableCount = 0
                previousCount = currentCount
            }
        }
    }
}
