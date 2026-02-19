package my.reviewing.reviewing_V2.review.repository

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.member.entity.Member
import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewRepository : JpaRepository<Review, Long> {
    fun existsByMemberAndCourseAndStateIn(
        member: Member,
        course: Course,
        states: List<ReviewStateType>
    ): Boolean
}
