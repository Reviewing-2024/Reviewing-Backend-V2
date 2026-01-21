package my.reviewing.reviewing_V2.course.service

import jakarta.transaction.Transactional
import my.reviewing.reviewing_V2.course.dto.PlatformResponseDto
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.global.api.ApiResponse
import org.springframework.stereotype.Service

@Service
@Transactional
class PlatformService(
    private val platformRepository: PlatformRepository
) {

    fun getAllPlatforms(): ApiResponse<List<PlatformResponseDto>> {
        val platforms = platformRepository.findAll()

        val platformDtos = platforms.map { platform ->
            PlatformResponseDto(
                id = platform.id!!,
                englishName = platform.englishName,
                koreanName = platform.koreanName
            )
        }

        return ApiResponse.ok(platformDtos)
    }

}