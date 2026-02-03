package my.reviewing.reviewing_V2.crawling.service

import com.fasterxml.jackson.databind.ObjectMapper
import my.reviewing.reviewing_V2.crawling.entity.Category
import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.entity.SubCategory
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CrawlingService(
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository,
    private val objectMapper: ObjectMapper
) {

    fun createPlatform(koreanName: String, englishName: String): Platform {

        val platform = Platform(
            koreanName = koreanName,
            englishName = englishName
        )

        return platformRepository.save(platform)

    }

    @Transactional
    fun createInflearnCategories(): Map<String, Int> {
        val platform = platformRepository.findByKoreanName("인프런")
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "인프런 플랫폼이 존재하지 않습니다. 먼저 플랫폼을 생성해주세요.")

        val resource = ClassPathResource("category/inflearn_categories.json")
        val jsonNode = objectMapper.readTree(resource.inputStream)

        var categoryCount = 0
        var subCategoryCount = 0

        jsonNode.forEach { categoryNode ->
            val categorySlug = categoryNode.get("slug").asText()
            val categoryTitle = categoryNode.get("title").asText()

            // 중복 체크 후 Category 저장
            val existingCategory = categoryRepository.findBySlugAndPlatform(categorySlug, platform)
            val category = existingCategory ?: run {
                categoryCount++
                categoryRepository.save(
                    Category(
                        name = categoryTitle,
                        slug = categorySlug,
                        platform = platform
                    )
                )
            }

            // children (SubCategory) 저장
            categoryNode.get("children")?.forEach { childNode ->
                val subSlug = childNode.get("slug").asText()
                val subTitle = childNode.get("title").asText()

                val existingSub = subCategoryRepository.findBySlugAndCategoryId(subSlug, category.id!!)
                if (existingSub == null) {
                    subCategoryCount++
                    subCategoryRepository.save(
                        SubCategory(
                            name = subTitle,
                            slug = subSlug,
                            category = category
                        )
                    )
                }
            }
        }

        return mapOf(
            "savedCategories" to categoryCount,
            "savedSubCategories" to subCategoryCount
        )
    }

}