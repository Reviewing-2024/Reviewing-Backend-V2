package my.reviewing.reviewing_V2.crawling.crawlingBatch.nomadcoders

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader
import java.time.Duration

open class NomadcodersReader(
    private val platformRepository: PlatformRepository,
    private val objectMapper: ObjectMapper
) : ItemStreamReader<Course> {

    private val log = LoggerFactory.getLogger(NomadcodersReader::class.java)

    companion object {
        private const val READ_COUNT_KEY = "nomadcoders.read.count"
    }

    private lateinit var driver: WebDriver
    private lateinit var platform: Platform
    private lateinit var courseKeys: List<String>
    private lateinit var apolloState: JsonNode

    private var currentIndex = 0

    override fun open(executionContext: ExecutionContext) {
        log.info("노마드코더 크롤링 시작")

        // 1. 이전 상태 복원 (재시작 시)
        currentIndex = executionContext.getInt(READ_COUNT_KEY, 0)
        if (currentIndex > 0) {
            log.info("재시작 감지: {}번째부터 이어서 처리", currentIndex)
        }

        // 2. Platform 조회
        platform = platformRepository.findByKoreanName("노마드코더")
            ?: throw ItemStreamException("노마드코더 플랫폼을 찾을 수 없습니다")

        // 3. ChromeDriver 초기화
        val options = ChromeOptions().apply {
            addArguments("--headless=new")
            addArguments("--no-sandbox")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--disable-blink-features=AutomationControlled")
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        driver = ChromeDriver(options)
        log.debug("ChromeDriver 초기화 완료")

        // 4. 페이지 접속 및 JSON 파싱
        val url = "https://nomadcoders.co/courses"
        log.info("페이지 접속: {}", url)

        try {
            driver.get(url)
            val wait = WebDriverWait(driver, Duration.ofSeconds(10))
            wait.until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("script#__NEXT_DATA__")
                )
            )
            log.debug("페이지 로드 완료")
        } catch (e: Exception) {
            log.error("페이지 접속 실패: {}", e.message)
            throw ItemStreamException("페이지 접속 실패", e)
        }

        val pageSource = driver.pageSource
            ?: throw ItemStreamException("페이지 소스를 가져올 수 없습니다")

        val jsonData = extractNextData(pageSource)
            ?: throw ItemStreamException("JSON 데이터를 찾을 수 없습니다")

        // 5. JSON 파싱 (Course 객체 생성은 안 함)
        val root: JsonNode = try {
            objectMapper.readTree(jsonData)
        } catch (e: Exception) {
            log.error("JSON 파싱 실패: {}", e.message)
            throw ItemStreamException("JSON 파싱 실패", e)
        }

        apolloState = root
            .path("props")
            .path("pageProps")
            .path("__APOLLO_STATE__")

        if (apolloState.isMissingNode) {
            log.error("Apollo state를 찾을 수 없습니다")
            throw ItemStreamException("Apollo state missing")
        }

        // 6. course:* 키만 추출 (Course 객체는 read()에서 생성)
        courseKeys = apolloState.fieldNames()
            .asSequence()
            .filter { it.startsWith("course:") }
            .toList()

        log.info("크롤링 완료: 총 {}개 강의 발견, {}번째부터 처리 시작", courseKeys.size, currentIndex)
    }

    override fun read(): Course? {
        // 모든 강의 처리 완료
        if (currentIndex >= courseKeys.size) {
            log.info("모든 강의 읽기 완료")
            return null
        }

        // 현재 인덱스의 강의를 Course 객체로 변환
        val key = courseKeys[currentIndex]
        val courseNode = apolloState.get(key)

        val course = Course(
            platform = platform,
            title = courseNode.path("name").asText(),
            slug = courseNode.path("slug").asText(),
            teacher = "니꼴라스",
            thumbnailImage = courseNode.path("thumbnail").asText(),
            url = "https://nomadcoders.co/${courseNode.path("slug").asText()}"
        )

//        log.debug("읽기 [{}/{}]: {}", currentIndex + 1, courseKeys.size, course.title)
        currentIndex++

        return course
    }

    override fun update(executionContext: ExecutionContext) {
        // chunk 완료 시마다 호출 → 현재 진행 상태 저장
        executionContext.putInt(READ_COUNT_KEY, currentIndex)
//        log.debug("진행 상태 저장: {}/{}", currentIndex, courseKeys.size)
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
}