package my.reviewing.reviewing_V2.review.dto

import my.reviewing.reviewing_V2.review.entity.Review
import java.math.BigDecimal
import java.time.Instant

data class ReviewResponseDto(
    val id: Long,
    val memberName: String,
    val content: String,
    val rating: BigDecimal,
    val likes: Int,
    val dislikes: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(review: Review) = ReviewResponseDto(
            id = review.id!!,
            memberName = review.member.name,
            content = review.content,
            rating = review.rating,
            likes = review.likes,
            dislikes = review.dislikes,
            createdAt = review.createdAt!!
        )
    }
}
