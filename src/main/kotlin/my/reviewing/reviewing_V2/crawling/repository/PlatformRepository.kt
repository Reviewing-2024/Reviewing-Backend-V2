package my.reviewing.reviewing_V2.crawling.repository

import my.reviewing.reviewing_V2.crawling.entity.Platform
import org.springframework.data.jpa.repository.JpaRepository

interface PlatformRepository : JpaRepository<Platform, Long> {

    fun findByKoreanName(koreanName: String): Platform?

}