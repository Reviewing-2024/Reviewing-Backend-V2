package my.reviewing.reviewing_V2.crawling.crawlingBatch.nomadcoders

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.ItemStreamReader
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate

open class NomadcodersReader(
    private val platformRepository: PlatformRepository,
    private val objectMapper: ObjectMapper
) : ItemStreamReader<Course> {

    private val log = LoggerFactory.getLogger(NomadcodersReader::class.java)

    companion object {
        private const val READ_COUNT_KEY = "nomadcoders.read.count"
    }

    private lateinit var platform: Platform
    private lateinit var courseKeys: List<String>
    private lateinit var apolloState: JsonNode

    private var currentIndex = 0

    override fun open(executionContext: ExecutionContext) {
        log.info("노마드코더 크롤링 시작")

        currentIndex = executionContext.getInt(READ_COUNT_KEY, 0)
        if (currentIndex > 0) {
            log.info("재시작 감지: {}번째부터 이어서 처리", currentIndex)
        }

        platform = platformRepository.findByKoreanName("노마드코더")
            ?: throw ItemStreamException("노마드코더 플랫폼을 찾을 수 없습니다")

        val url = "https://nomadcoders.co/courses"
        log.info("페이지 접속: {}", url)

        val html = try {
            val headers = HttpHeaders().apply {
                set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                set("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            val response = RestTemplate().exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
            response.body ?: throw ItemStreamException("응답 body가 null입니다")
        } catch (e: Exception) {
            log.error("페이지 접속 실패: {}", e.message)
            throw ItemStreamException("페이지 접속 실패", e)
        }

        log.debug("페이지 로드 완료")

        val jsonData = extractNextData(html)
            ?: throw ItemStreamException("JSON 데이터를 찾을 수 없습니다")

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