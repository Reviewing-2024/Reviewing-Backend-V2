package my.reviewing.reviewing_V2.crawling.service.nomadcoders

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.time.Duration
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader

class NomadcodersReader(
    private val platformRepository: PlatformRepository,
) : ItemStreamReader<Course> {

    private val log = LoggerFactory.getLogger(NomadcodersReader::class.java)

    private lateinit var driver: WebDriver
    private val courseList = mutableListOf<Course>()
    private lateinit var courseIterator: Iterator<Course>

    override fun open(executionContext: ExecutionContext) {
        log.info("노마드코더 크롤링 시작")

        val options = ChromeOptions().apply {
            addArguments("--headless=new")
            addArguments("--no-sandbox")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--disable-blink-features=AutomationControlled")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }

        driver = ChromeDriver(options)
        log.debug("ChromeDriver 초기화 완료")

        val platform = platformRepository.findByKoreanName("노마드코더")
            ?: throw ItemStreamException("노마드코더 플랫폼을 찾을 수 없습니다")

        val url = "https://nomadcoders.co/courses"
        log.info("페이지 접속: {}", url)

        try {
            driver.get(url)

            // __NEXT_DATA__ 스크립트 태그가 로드될 때까지 최대 10초 대기
            val wait = WebDriverWait(driver, Duration.ofSeconds(10))
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("script#__NEXT_DATA__")
            ))
            log.debug("페이지 로드 완료")
        } catch (e: Exception) {
            log.error("페이지 접속 실패: {}", e.message)
            throw ItemStreamException("페이지 접속 실패", e)
        }

        val pageSource = driver.pageSource
            ?: throw ItemStreamException("페이지 소스를 가져올 수 없습니다")

        val jsonData = extractNextData(pageSource)
            ?: throw ItemStreamException("JSON 데이터를 찾을 수 없습니다")

        // Jackson으로 JSON 파싱
        val mapper = ObjectMapper()
        val root: JsonNode = try {
            mapper.readTree(jsonData)
        } catch (e: Exception) {
            log.error("JSON 파싱 실패: {}", e.message)
            throw ItemStreamException("JSON 파싱 실패", e)
        }

        val apolloState: JsonNode = root
            .path("props")
            .path("pageProps")
            .path("__APOLLO_STATE__")

        if (apolloState.isMissingNode) {
            log.error("Apollo state를 찾을 수 없습니다")
            throw ItemStreamException("Apollo state missing")
        }

        // course:* 키를 가진 모든 강의 추출
        val fieldNames = apolloState.fieldNames()

        while (fieldNames.hasNext()) {
            val key = fieldNames.next()

            if (key.startsWith("course:")) {
                val courseNode = apolloState.get(key)

                val title = courseNode.path("name").asText()
                val slug = courseNode.path("slug").asText()
                val thumbnail = courseNode.path("thumbnail").asText()
                val courseUrl = "https://nomadcoders.co/$slug"

                val course = Course(
                    platform = platform,
                    title = title,
                    slug = slug,
                    teacher = "니꼴라스",
                    thumbnailImage = thumbnail,
                    url = courseUrl
                )

                courseList.add(course)
                log.debug("강의 추출: {}", title)
            }
        }

        courseIterator = courseList.iterator()
        log.info("크롤링 완료: 총 {}개 강의 추출", courseList.size)
    }

    private fun extractNextData(pageSource: String): String? {
        val startIndex = pageSource.indexOf("__NEXT_DATA__")
        if (startIndex == -1) {
            log.warn("__NEXT_DATA__ 태그를 찾을 수 없습니다")
            return null
        }

        val jsonStart = pageSource.indexOf("{", startIndex)
        val jsonEnd = pageSource.indexOf("</script>", jsonStart)

        return if (jsonStart != -1 && jsonEnd != -1) {
            pageSource.substring(jsonStart, jsonEnd).trim()
        } else {
            log.warn("JSON 데이터 추출 실패")
            null
        }
    }

    override fun read(): Course? {
        return if (courseIterator.hasNext()) {
            val course = courseIterator.next()
            log.debug("읽기: {}", course.title)
            course
        } else {
            log.info("모든 강의 읽기 완료")
            null
        }
    }

    override fun update(executionContext: ExecutionContext) {
        // 재시작 시 상태 저장 필요하면 구현
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
}