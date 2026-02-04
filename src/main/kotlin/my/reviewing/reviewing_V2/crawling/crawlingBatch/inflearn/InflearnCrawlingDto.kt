package my.reviewing.reviewing_V2.crawling.crawlingBatch.inflearn

import my.reviewing.reviewing_V2.crawling.entity.Platform
import my.reviewing.reviewing_V2.crawling.entity.SubCategory

/**
 * 인프런 크롤링 시 사용하는 DTO
 * - Reader에서 크롤링한 데이터를 담음
 * - Processor에서 Course 엔티티로 변환 + SubCategoryCourse 매핑
 */
data class InflearnCrawlingDto(
    val platform: Platform,
    val subCategory: SubCategory,
    val title: String,
    val courseUrl: String,
    val courseSlug: String,
    val thumbnailImage: String?,
    val thumbnailVideo: String?,
    val teacher: String
)
