package my.reviewing.reviewing_V2.course.service

import my.reviewing.reviewing_V2.course.dto.CategoryResponseDto
import my.reviewing.reviewing_V2.crawling.repository.CategoryRepository
import my.reviewing.reviewing_V2.crawling.repository.PlatformRepository
import my.reviewing.reviewing_V2.crawling.repository.SubCategoryRepository
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val platformRepository: PlatformRepository,
    private val categoryRepository: CategoryRepository,
    private val subCategoryRepository: SubCategoryRepository
) {

    fun getCategoriesByPlatform(platformName: String): ApiResponse<List<CategoryResponseDto>> {
        val platform = platformRepository.findByEnglishName(platformName)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "플랫폼을 찾을 수 없습니다: $platformName")

        val categories = categoryRepository.findByPlatform(platform)

        return ApiResponse.ok(categories.map { category ->
            CategoryResponseDto(
                id = category.id!!,
                name = category.name,
                slug = category.slug
            )
        })
    }

    fun getSubCategoriesByCategory(
        platformName: String,
        categorySlug: String
    ): ApiResponse<List<CategoryResponseDto>> {

        val subCategories = subCategoryRepository.findByCategorySlugAndCategoryPlatformEnglishName(
            categorySlug,
            platformName
        )

        return ApiResponse.ok(subCategories.map { subCategory ->
            CategoryResponseDto(
                id = subCategory.id!!,
                name = subCategory.name,
                slug = subCategory.slug
            )
        })

    }

}