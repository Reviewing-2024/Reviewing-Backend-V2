package my.reviewing.reviewing_V2.review.dto

import my.reviewing.reviewing_V2.review.entity.Review
import java.math.BigDecimal
import java.time.Instant

data class ReviewResponseDto(
    val id: Long,
    val memberName: String,
    val memberProfileImage: String,
    val content: String,
    val rating: BigDecimal,
    val likes: Int,
    val dislikes: Int,
    val liked: Boolean,
    val disliked: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(review: Review, liked: Boolean = false, disliked: Boolean = false) = ReviewResponseDto(
            id = review.id!!,
            memberName = review.member.name,
            memberProfileImage = review.member.profileImage,
            content = review.content,
            rating = review.rating,
            likes = review.likes,
            dislikes = review.dislikes,
            liked = liked,
            disliked = disliked,
            createdAt = review.createdAt!!
        )
    }
}
