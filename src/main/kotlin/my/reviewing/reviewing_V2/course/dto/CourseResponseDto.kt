package my.reviewing.reviewing_V2.course.dto

import java.math.BigDecimal
import java.util.UUID

class CourseResponseDto(

    val id: UUID,
    val slug: String,
    val title: String,
    val teacher: String,
    val thumbnailImage: String?,
    val thumbnailVideo: String?,
    val url: String,
    val rating: BigDecimal,
    val wishes: Int,
    val comments: Int,
    val platform: String,
    val wished: Boolean = false

)