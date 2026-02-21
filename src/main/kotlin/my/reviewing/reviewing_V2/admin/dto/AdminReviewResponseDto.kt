package my.reviewing.reviewing_V2.admin.dto

import my.reviewing.reviewing_V2.review.entity.Review
import java.time.Instant

class AdminReviewResponseDto(
    val id: Long,
    val courseTitle: String,
    val courseUrl: String,
    val memberName: String,
    val content: String,
    val certificaton: String,
    val createdAt: Instant
) {
    companion object {
        fun from(review: Review) = AdminReviewResponseDto(
            id = review.id!!,
            courseTitle = review.course.title,
            courseUrl = review.course.url,
            memberName = review.member.name,
            content = review.content,
            certificaton = review.certification,
            createdAt = review.createdAt!!
        )
    }
}