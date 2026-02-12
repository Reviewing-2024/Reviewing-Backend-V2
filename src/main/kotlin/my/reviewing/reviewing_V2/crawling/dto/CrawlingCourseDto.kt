package my.reviewing.reviewing_V2.crawling.dto

import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.entity.SubCategory

/**
 * 강의 크롤링 공통 DTO
 * - 인프런, 코드잇 등 여러 플랫폼에서 재사용
 * - Reader에서 크롤링한 데이터를 담음
 * - Processor에서 Course 엔티티로 변환 + SubCategoryCourse 매핑
 */
data class CrawlingCourseDto(
    val platform: Platform,
    val subCategory: SubCategory,
    val title: String,
    val courseUrl: String,
    val courseSlug: String,
    val thumbnailImage: String?,
    val thumbnailVideo: String?,
    val teacher: String
)
