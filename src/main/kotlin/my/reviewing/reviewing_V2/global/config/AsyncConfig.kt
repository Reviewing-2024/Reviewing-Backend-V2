package my.reviewing.reviewing_V2.global.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    private val log = LoggerFactory.getLogger(AsyncConfig::class.java)

    /**
     * 크롤링 전용 스레드 풀
     * - corePoolSize: 기본 스레드 수
     * - maxPoolSize: 최대 스레드 수
     * - queueCapacity: 대기 큐 크기 (큐가 가득 차면 maxPoolSize까지 스레드 생성)
     * - CallerRunsPolicy: 큐도 가득 차면 호출한 스레드에서 직접 실행 (요청 거부 방지)
     */
    @Bean("crawlingExecutor")
    fun crawlingExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 2
        executor.queueCapacity = 20
        executor.setThreadNamePrefix("crawling-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }

    /**
     * 기본 비동기 Executor (@Async에 이름 안 붙이면 이거 사용)
     */
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 10
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("async-")
        executor.initialize()
        return executor
    }

    /**
     * 비동기 메서드에서 예외 발생 시 처리
     */
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex: Throwable, method: Method, params: Array<Any> ->
            log.error("비동기 작업 예외 발생 - 메서드: {}, 파라미터: {}, 예외: {}",
                method.name, params.contentToString(), ex.message, ex)
        }
    }
}