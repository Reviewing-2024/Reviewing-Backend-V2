package my.reviewing.reviewing_V2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.util.TimeZone

@SpringBootApplication
@EnableJpaAuditing
class ReviewingV2Application

fun main(args: Array<String>) {
	TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
	runApplication<ReviewingV2Application>(*args)
}
