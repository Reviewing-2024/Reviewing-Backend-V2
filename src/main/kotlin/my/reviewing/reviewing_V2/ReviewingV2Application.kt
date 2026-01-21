package my.reviewing.reviewing_V2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class ReviewingV2Application

fun main(args: Array<String>) {
	runApplication<ReviewingV2Application>(*args)
}
