package my.reviewing.reviewing_V2.crawling.service

import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import org.springframework.stereotype.Service

@Service
class CrawlingService (
    private val platformRepository: PlatformRepository
){

    fun createPlatform(koreanName: String, englishName: String): Platform {

        val platform = Platform(
            koreanName = koreanName,
            englishName = englishName
        )

        return platformRepository.save(platform)

    }



}