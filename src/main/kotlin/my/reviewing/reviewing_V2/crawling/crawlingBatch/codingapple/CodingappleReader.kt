package my.reviewing.reviewing_V2.crawling.crawlingBatch.codingapple

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
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
 * 코딩애플 강의 크롤링 Reader
 *
 * - 단일 페이지 크롤링 (https://codingapple.com/all-courses/)
 * - 카테고리/페이지네이션 없음 → Course 직접 반환 (CrawlingCourseDto 미사용)
 * - SSR 페이지 → 단순 DOM 파싱 (scrollToBottom 불필요)
 * - 재시작 지원 (ExecutionContext)
 *
 * HTML 구조:
 * ul#course-list
 *   li.course_single_item
 *     div.item-avatar > a[href] > img[src]   ← URL + 썸네일
 *     div.item-title > a                     ← 제목
 */
class CodingappleReader(
    private val platformRepository: PlatformRepository
) : ItemStreamReader<Course> {

    private val log = LoggerFactory.getLogger(CodingappleReader::class.java)

    companion object {
        private const val READ_COUNT_KEY = "codingapple.read.count"
        private const val URL = "https://codingapple.com/all-courses/"

        private const val SELECTOR_COURSE_LIST = "ul#course-list"
        private const val SELECTOR_COURSE_ITEM = "li.course_single_item"
        private const val SELECTOR_AVATAR_LINK = "div.item-avatar a"
        private const val SELECTOR_THUMBNAIL = "div.item-avatar a img"
        private const val SELECTOR_TITLE = "div.item-title a"
    }

    private lateinit var driver: WebDriver
    private lateinit var platform: Platform
    private lateinit var courses: List<Course>
    private var currentIndex = 0

    override fun open(executionContext: ExecutionContext) {
        currentIndex = executionContext.getInt(READ_COUNT_KEY, 0)
        if (currentIndex > 0) {
            log.info("재시작 감지: {}번째부터 이어서 처리", currentIndex)
        }

        platform = platformRepository.findByKoreanName("코딩애플")
            ?: throw ItemStreamException("코딩애플 플랫폼을 찾을 수 없습니다. 먼저 플랫폼을 생성해주세요.")

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

        try {
            driver.get(URL)
            val wait = WebDriverWait(driver, Duration.ofSeconds(30))
            wait.until { d ->
                d.findElements(By.cssSelector(SELECTOR_COURSE_LIST)).isNotEmpty()
            }
        } catch (e: Exception) {
            driver.quit()
            throw ItemStreamException("페이지 접속 실패: ${e.message}", e)
        }

        courses = parseCourses()
        log.info("총 {}개 강의 발견, {}번째부터 처리 시작", courses.size, currentIndex)
    }

    override fun read(): Course? {
        if (currentIndex >= courses.size) {
            log.info("코딩애플 크롤링 완료 (총 {}개)", courses.size)
            return null
        }
        return courses[currentIndex++]
    }

    override fun update(executionContext: ExecutionContext) {
        executionContext.putInt(READ_COUNT_KEY, currentIndex)
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

    private fun parseCourses(): List<Course> {
        val items = driver.findElements(By.cssSelector(SELECTOR_COURSE_ITEM))
        if (items.isEmpty()) {
            log.warn("⚠️ [셀렉터 변경 감지] 강의 목록을 찾을 수 없음. 셀렉터: {}", SELECTOR_COURSE_ITEM)
        }

        val result = mutableListOf<Course>()
        var failCount = 0

        for (item in items) {
            val course = parseCourseItem(item)
            if (course != null) result.add(course)
            else failCount++
        }

        if (items.isNotEmpty() && failCount > items.size / 2) {
            log.warn("⚠️ [셀렉터 변경 감지] 파싱 실패율이 높음: {}/{} 실패. 셀렉터 확인 필요", failCount, items.size)
        }

        log.info("{}개 강의 파싱 완료 (실패: {}개)", result.size, failCount)
        return result
    }

    private fun parseCourseItem(item: WebElement): Course? {
        val avatarLink = try {
            item.findElement(By.cssSelector(SELECTOR_AVATAR_LINK))
        } catch (_: Exception) { return null }

        val courseUrl = avatarLink.getAttribute("href") ?: return null
        val slug = courseUrl.trimEnd('/').substringAfterLast("/")
        if (slug.isEmpty()) return null

        val thumbnailImage = try {
            item.findElement(By.cssSelector(SELECTOR_THUMBNAIL)).getAttribute("src")
        } catch (_: Exception) {
            log.debug("⚠️ [셀렉터 변경 감지] 썸네일 없음. 셀렉터: {}", SELECTOR_THUMBNAIL)
            null
        }

        val title = try {
            val titleEl = item.findElement(By.cssSelector(SELECTOR_TITLE))
            ((driver as JavascriptExecutor)
                .executeScript("return arguments[0].textContent", titleEl) as? String)
                ?.trim() ?: return null
        } catch (_: Exception) {
            log.debug("⚠️ [셀렉터 변경 감지] 제목 없음. 셀렉터: {}", SELECTOR_TITLE)
            return null
        }

        if (title.isEmpty()) return null

        return Course(
            platform = platform,
            title = title,
            slug = slug,
            url = courseUrl,
            thumbnailImage = thumbnailImage,
            thumbnailVideo = null,
            teacher = "codingapple"
        )
    }
}
