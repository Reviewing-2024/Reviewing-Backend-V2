package my.reviewing.reviewing_V2.search.dto

import java.util.UUID

data class RecommendResponseDto(
    val intro: String,
    val recommendations: List<RecommendItem>
)

data class RecommendItem(
    val id: UUID,
    val title: String,
    val teacher: String?,
    val platform: String,
    val slug: String
)

data class GptRecommendResponse(
    val intro: String,
    val recommendations: List<GptRecommendItem>
)

data class GptRecommendItem(
    val index: Int
)
