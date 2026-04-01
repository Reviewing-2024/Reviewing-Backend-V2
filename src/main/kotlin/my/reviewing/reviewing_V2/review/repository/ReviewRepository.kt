package my.reviewing.reviewing_V2.review.repository

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.member.entity.Member
import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewRepository : JpaRepository<Review, Long> {
    fun existsByMemberAndCourseAndStateIn(
        member: Member,
        course: Course,
        states: List<ReviewStateType>
    ): Boolean

    fun findByCourseAndState(course: Course, state: ReviewStateType, pageable: Pageable): Slice<Review>
    fun findByState(state: ReviewStateType, pageable: Pageable): Slice<Review>
    fun countByState(state: ReviewStateType): Long

    @Modifying
    @Query("UPDATE Review r SET r.likes = r.likes + 1 WHERE r.id = :reviewId")
    fun incrementLikes(@Param("reviewId") reviewId: Long)

    @Modifying
    @Query("UPDATE Review r SET r.likes = r.likes - 1 WHERE r.id = :reviewId")
    fun decrementLikes(@Param("reviewId") reviewId: Long)

    @Modifying
    @Query("UPDATE Review r SET r.dislikes = r.dislikes + 1 WHERE r.id = :reviewId")
    fun incrementDislikes(@Param("reviewId") reviewId: Long)

    @Modifying
    @Query("UPDATE Review r SET r.dislikes = r.dislikes - 1 WHERE r.id = :reviewId")
    fun decrementDislikes(@Param("reviewId") reviewId: Long)

}
