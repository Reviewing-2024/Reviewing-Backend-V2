package my.reviewing.reviewing_V2.review.dto

import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class MyReviewResponseDto(
    val reviewId: Long,
    val content: String,
    val rating: BigDecimal,
    val likes: Int,
    val dislikes: Int,
    val state: ReviewStateType,
    val certification: String,
    val createdAt: Instant,
    val courseId: UUID,
    val courseTitle: String,
    val coursePlatform: String,
    val courseThumbnailImage: String?,
    val courseUrl: String,
    val courseSlug: String
) {
    companion object {
        fun from(review: Review) = MyReviewResponseDto(
            reviewId = review.id!!,
            content = review.content,
            rating = review.rating,
            likes = review.likes,
            dislikes = review.dislikes,
            state = review.state,
            certification = review.certification,
            createdAt = review.createdAt!!,
            courseId = review.course.id!!,
            courseTitle = review.course.title,
            coursePlatform = review.course.platform.englishName,
            courseThumbnailImage = review.course.thumbnailImage,
            courseUrl = review.course.url,
            courseSlug = review.course.slug
        )
    }
}
