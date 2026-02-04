package my.reviewing.reviewing_V2.crawling.crawlingBatch.inflearn

import my.reviewing.reviewing_V2.crawling.entity.Category
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.entity.SubCategory
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * 인프런 강의 크롤링 Reader
 *
 * @param maxCategories 최대 카테고리 수 (0 = 무제한, 테스트용)
 * @param maxSubCategoriesPerCategory 카테고리당 최대 서브카테고리 수 (0 = 무제한)
 * @param maxPagesPerSubCategory 서브카테고리당 최대 페이지 수 (0 = 무제한)
 */
class InflearnReader(
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val maxCategories: Int = 0,
    private val maxSubCategoriesPerCategory: Int = 0,
    private val maxPagesPerSubCategory: Int = 0
) : ItemStreamReader<InflearnCrawlingDto> {

    private val log = LoggerFactory.getLogger(InflearnReader::class.java)

    companion object {
        private const val CATEGORY_INDEX_KEY = "inflearn.category.index"
        private const val SUBCATEGORY_INDEX_KEY = "inflearn.subcategory.index"
        private const val PAGE_NUMBER_KEY = "inflearn.page.number"
        private const val BASE_URL = "https://www.inflearn.com/courses"

        // 셀렉터 상수 (변경 감지용)
        private const val SELECTOR_COURSE_LIST = "ul.mantine-1avyp1d"
        private const val SELECTOR_COURSE_ITEM = "li.mantine-1avyp1d"
        private const val SELECTOR_THUMBNAIL = "div.mantine-AspectRatio-root"
        private const val SELECTOR_TITLE = "p.mantine-Text-root"
        private const val SELECTOR_TEACHER = "p.mantine-Text-root.mantine-aiouth"
        private const val SELECTOR_PAGINATION = "button.mantine-Pagination-control"
    }

    private lateinit var driver: WebDriver
    private lateinit var platform: Platform

    // 카테고리 순회용
    private lateinit var categories: List<Category>
    private var categoryIndex = 0
    private var maxCategoryIndex = Int.MAX_VALUE  // 실제 적용될 최대 인덱스

    // 서브카테고리 순회용
    private var subCategories: List<SubCategory> = emptyList()
    private var subCategoryIndex = 0
    private var maxSubCategoryIndex = Int.MAX_VALUE  // 실제 적용될 최대 인덱스

    // 페이지네이션
    private var currentPage = 1
    private var lastPage = 1

    // 현재 페이지의 강의 목록
    private var courseBuffer: MutableList<InflearnCrawlingDto> = mutableListOf()

    override fun open(executionContext: ExecutionContext) {
        log.info("인프런 크롤링 시작")

        // 1. 이전 상태 복원 (재시작 시)
        categoryIndex = executionContext.getInt(CATEGORY_INDEX_KEY, 0)
        subCategoryIndex = executionContext.getInt(SUBCATEGORY_INDEX_KEY, 0)
        currentPage = executionContext.getInt(PAGE_NUMBER_KEY, 1)

        if (categoryIndex > 0 || subCategoryIndex > 0 || currentPage > 1) {
            log.info("재시작 감지: categoryIndex={}, subCategoryIndex={}, page={}",
                categoryIndex, subCategoryIndex, currentPage)
        }

        // 2. Platform 조회
        platform = platformRepository.findByKoreanName("인프런")
            ?: throw ItemStreamException("인프런 플랫폼을 찾을 수 없습니다")

        // 3. 카테고리 목록 조회
        categories = categoryRepository.findByPlatform(platform)
        if (categories.isEmpty()) {
            throw ItemStreamException("인프런 카테고리가 없습니다. 먼저 카테고리를 생성해주세요.")
        }

        // 4. 제한 옵션 적용
        maxCategoryIndex = if (maxCategories > 0) {
            minOf(maxCategories, categories.size)
        } else {
            categories.size
        }

        if (maxCategories > 0 || maxSubCategoriesPerCategory > 0 || maxPagesPerSubCategory > 0) {
            log.info("🧪 테스트 모드 - 제한 설정: 카테고리={}개, 서브카테고리={}개/카테고리, 페이지={}개/서브카테고리",
                if (maxCategories > 0) maxCategories else "무제한",
                if (maxSubCategoriesPerCategory > 0) maxSubCategoriesPerCategory else "무제한",
                if (maxPagesPerSubCategory > 0) maxPagesPerSubCategory else "무제한"
            )
        }

        log.info("총 {}개 카테고리 발견 (크롤링 대상: {}개)", categories.size, maxCategoryIndex)

        // 5. ChromeDriver 초기화
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

        // 5. 셀렉터 검증
        if (categoryIndex == 0 && subCategoryIndex == 0 && currentPage == 1) {
            validateSelectors()
        }

        // 6. 재시작 시 해당 카테고리의 서브카테고리 로드
        if (categoryIndex < categories.size) {
            loadSubCategories(categories[categoryIndex])
        }
    }

    /**
     * 크롤링 시작 전 셀렉터 유효성 검증
     * - 인프런 페이지 구조 변경 시 조기 감지
     */
    private fun validateSelectors() {
        log.info("=== 셀렉터 유효성 검증 시작 ===")

        try {
            // 테스트용 URL (it-programming/web-dev는 항상 강의가 많음)
            val testUrl = "$BASE_URL/it-programming/web-dev?types=ONLINE&page_number=1"
            driver.get(testUrl)
            waitForPageLoad()
            Thread.sleep(3000)

            val selectorResults = mutableMapOf<String, Boolean>()

            // 1. 강의 목록 셀렉터 검증
            val courseList = driver.findElements(By.cssSelector(SELECTOR_COURSE_LIST))
            selectorResults["COURSE_LIST ($SELECTOR_COURSE_LIST)"] = courseList.isNotEmpty()

            // 2. 강의 아이템 셀렉터 검증
            val courseItems = driver.findElements(By.cssSelector(SELECTOR_COURSE_ITEM))
            selectorResults["COURSE_ITEM ($SELECTOR_COURSE_ITEM)"] = courseItems.isNotEmpty()

            // 3. 썸네일 셀렉터 검증
            val thumbnails = driver.findElements(By.cssSelector(SELECTOR_THUMBNAIL))
            selectorResults["THUMBNAIL ($SELECTOR_THUMBNAIL)"] = thumbnails.isNotEmpty()

            // 4. 제목 셀렉터 검증
            val titles = driver.findElements(By.cssSelector(SELECTOR_TITLE))
            selectorResults["TITLE ($SELECTOR_TITLE)"] = titles.isNotEmpty()

            // 5. 강사 셀렉터 검증
            val teachers = driver.findElements(By.cssSelector(SELECTOR_TEACHER))
            selectorResults["TEACHER ($SELECTOR_TEACHER)"] = teachers.isNotEmpty()

            // 6. 페이지네이션 셀렉터 검증
            val pagination = driver.findElements(By.cssSelector(SELECTOR_PAGINATION))
            selectorResults["PAGINATION ($SELECTOR_PAGINATION)"] = pagination.isNotEmpty()

            // 결과 로깅
            var failedCount = 0
            for ((selector, found) in selectorResults) {
                if (found) {
                    log.info("✅ {} - OK", selector)
                } else {
                    log.warn("❌ {} - NOT FOUND", selector)
                    failedCount++
                }
            }

            if (failedCount > 0) {
                log.warn("⚠️ === 셀렉터 검증 결과: {}개 실패 - 인프런 페이지 구조 변경 가능성 있음 ===", failedCount)
            } else {
                log.info("=== 셀렉터 검증 완료: 모든 셀렉터 정상 ===")
            }

        } catch (e: Exception) {
            log.warn("셀렉터 검증 중 오류 발생 (크롤링은 계속 진행): {}", e.message)
        }
    }

    override fun read(): InflearnCrawlingDto? {
        // 1. 버퍼에 데이터가 있으면 반환
        if (courseBuffer.isNotEmpty()) {
            return courseBuffer.removeAt(0)
        }

        // 2. 현재 페이지에서 더 가져올 수 있으면 크롤링
        if (currentPage <= lastPage && subCategoryIndex < maxSubCategoryIndex) {
            crawlCurrentPage()
            if (courseBuffer.isNotEmpty()) {
                return courseBuffer.removeAt(0)
            }
        }

        // 3. 다음 서브카테고리로 이동 (제한 범위 내에서)
        while (subCategoryIndex < maxSubCategoryIndex - 1 || categoryIndex < maxCategoryIndex - 1) {
            // 다음 서브카테고리
            if (subCategoryIndex < maxSubCategoryIndex - 1) {
                subCategoryIndex++
                currentPage = 1
                findLastPage()
                crawlCurrentPage()
                if (courseBuffer.isNotEmpty()) {
                    return courseBuffer.removeAt(0)
                }
            }
            // 다음 카테고리
            else if (categoryIndex < maxCategoryIndex - 1) {
                categoryIndex++
                subCategoryIndex = 0
                currentPage = 1
                loadSubCategories(categories[categoryIndex])
                if (subCategories.isNotEmpty() && maxSubCategoryIndex > 0) {
                    findLastPage()
                    crawlCurrentPage()
                    if (courseBuffer.isNotEmpty()) {
                        return courseBuffer.removeAt(0)
                    }
                }
            }
        }

        log.info("크롤링 완료 (카테고리: {}/{}, 서브카테고리: {}/{})",
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

        // 서브카테고리 제한 적용
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

    private fun findLastPage() {
        if (subCategories.isEmpty() || subCategoryIndex >= subCategories.size) {
            lastPage = 1
            return
        }

        val category = categories[categoryIndex]
        val subCategory = subCategories[subCategoryIndex]
        val url = "$BASE_URL/${category.slug}/${subCategory.slug}?types=ONLINE&page_number=1"

        log.info("마지막 페이지 조회: {}", url)

        try {
            driver.get(url)
            waitForPageLoad()
            Thread.sleep(2000)

            val paginationButtons = driver.findElements(By.cssSelector(SELECTOR_PAGINATION))

            // 🔍 셀렉터 변경 감지 로깅
            if (paginationButtons.isEmpty()) {
                log.warn("⚠️ [셀렉터 변경 감지] 페이지네이션 버튼을 찾을 수 없음. 셀렉터: {}", SELECTOR_PAGINATION)
                log.warn("⚠️ 현재 URL: {}", url)
            }

            var foundLastPage = 1
            for (button in paginationButtons) {
                val text = button.text.trim()
                if (text.matches(Regex("\\d+"))) {
                    val pageNum = text.toInt()
                    foundLastPage = maxOf(foundLastPage, pageNum)
                }
            }

            // 페이지 제한 적용
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
            log.warn("페이지 수 조회 실패, 1페이지만 크롤링: {}", e.message)
            lastPage = 1
        }
    }

    private fun crawlCurrentPage() {
        if (subCategories.isEmpty() || subCategoryIndex >= subCategories.size) {
            return
        }

        val category = categories[categoryIndex]
        val subCategory = subCategories[subCategoryIndex]
        val url = "$BASE_URL/${category.slug}/${subCategory.slug}?types=ONLINE&page_number=$currentPage"

        log.info("크롤링: {} (페이지 {}/{})", url, currentPage, lastPage)

        try {
            driver.get(url)
            waitForPageLoad()
            Thread.sleep(2000)

            // 강의 목록 대기
            val wait = WebDriverWait(driver, Duration.ofSeconds(10))
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(SELECTOR_COURSE_LIST)
                ))
            } catch (e: Exception) {
                log.warn("⚠️ [셀렉터 변경 감지] 강의 목록을 찾을 수 없음. 셀렉터: {}", SELECTOR_COURSE_LIST)
                log.warn("⚠️ 현재 URL: {}", url)
                currentPage++
                return
            }

            // 페이지 하단까지 스크롤 (강의가 2개 섹션으로 나뉨: 강의 20개 → 지식공유자 → 강의 20개)
            (driver as JavascriptExecutor).executeScript("window.scrollTo(0, document.body.scrollHeight)")
            Thread.sleep(2000)

            // 모든 강의 리스트 찾기
            val allCourseLists = driver.findElements(By.cssSelector(SELECTOR_COURSE_LIST))
            log.info("UL 요소 {}개 발견", allCourseLists.size)

            val allCourses = mutableListOf<WebElement>()

            for ((index, courseList) in allCourseLists.withIndex()) {
                val courses = courseList.findElements(By.cssSelector(SELECTOR_COURSE_ITEM))
                log.info("UL[{}] 에서 LI 요소 {}개 발견", index, courses.size)
                allCourses.addAll(courses)
            }

            log.info("총 {}개 강의 요소 발견", allCourses.size)

            var parseFailCount = 0
            for (courseElement in allCourses) {
                try {
                    val dto = parseCourseElement(courseElement, subCategory)
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

            // 🔍 파싱 실패율 감지 로깅
            if (allCourses.isNotEmpty() && parseFailCount > allCourses.size / 2) {
                log.warn("⚠️ [셀렉터 변경 감지] 파싱 실패율이 높음: {}/{} 실패", parseFailCount, allCourses.size)
                log.warn("⚠️ 썸네일/제목/강사 셀렉터 확인 필요")
            }

            log.info("{}개 강의 크롤링 완료 (실패: {}개)", courseBuffer.size, parseFailCount)
            currentPage++

        } catch (e: Exception) {
            log.error("페이지 크롤링 실패: {}", e.message)
            currentPage++
        }
    }

    private fun parseCourseElement(element: WebElement, subCategory: SubCategory): InflearnCrawlingDto? {
        // 링크 찾기
        val links = element.findElements(By.tagName("a"))
        if (links.isEmpty()) return null

        val link = links[0]
        val baseCourseUrl = link.getAttribute("href") ?: return null

        // 강의 URL이 아니면 스킵 (지식공유자 등 다른 섹션)
        if (!baseCourseUrl.contains("/course/")) {
            log.info("강의 아닌 요소 스킵: {}", baseCourseUrl)
            return null
        }

        // URL 정리 (attributionToken 제거)
        val keywordIndex = baseCourseUrl.indexOf("attributionToken")
        val encodedUrl = if (keywordIndex != -1) {
            baseCourseUrl.substring(0, baseCourseUrl.lastIndexOf("?", keywordIndex))
        } else {
            baseCourseUrl
        }
        val courseUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8)
        val courseSlug = courseUrl.split("/").last()

        // 썸네일 (이미지 또는 비디오)
        var thumbnailImage: String? = null
        var thumbnailVideo: String? = null

        try {
            val aspectRatio = element.findElement(By.cssSelector(SELECTOR_THUMBNAIL))
            try {
                thumbnailImage = aspectRatio.findElement(By.tagName("img")).getAttribute("src")
            } catch (imgEx: NoSuchElementException) {
                try {
                    thumbnailVideo = aspectRatio.findElement(By.tagName("source")).getAttribute("src")
                } catch (videoEx: NoSuchElementException) {
                    log.debug("⚠️ [셀렉터 변경 감지] 썸네일 img/source 없음. 강의: {}", courseSlug)
                }
            }
        } catch (e: NoSuchElementException) {
            log.debug("⚠️ [셀렉터 변경 감지] 썸네일 컨테이너 없음. 셀렉터: {}, 강의: {}", SELECTOR_THUMBNAIL, courseSlug)
        }

        // 제목
        val title = try {
            element.findElement(By.tagName("a"))
                .findElement(By.cssSelector(SELECTOR_TITLE))
                .text
        } catch (e: NoSuchElementException) {
            log.debug("⚠️ [셀렉터 변경 감지] 제목 요소 없음. 셀렉터: {}, 강의: {}", SELECTOR_TITLE, courseSlug)
            return null
        }

        // 강사
        val teacher = try {
            element.findElement(By.cssSelector(SELECTOR_TEACHER)).text
        } catch (e: NoSuchElementException) {
            log.debug("⚠️ [셀렉터 변경 감지] 강사 요소 없음. 셀렉터: {}, 강의: {}", SELECTOR_TEACHER, courseSlug)
            "Unknown"
        }

        return InflearnCrawlingDto(
            platform = platform,
            subCategory = subCategory,
            title = title,
            courseUrl = courseUrl,
            courseSlug = courseSlug,
            thumbnailImage = thumbnailImage,
            thumbnailVideo = thumbnailVideo,
            teacher = teacher
        )
    }

    private fun waitForPageLoad() {
        val wait = WebDriverWait(driver, Duration.ofSeconds(30))
        wait.until { webDriver ->
            (webDriver as JavascriptExecutor)
                .executeScript("return document.readyState") == "complete"
        }
    }
}
