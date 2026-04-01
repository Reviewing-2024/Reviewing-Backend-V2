package my.reviewing.reviewing_V2.review.repository

import my.reviewing.reviewing_V2.review.entity.ReviewLike
import my.reviewing.reviewing_V2.review.entity.ReviewLikeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewLikeRepository : JpaRepository<ReviewLike, Long> {

    fun existsByReviewIdAndMemberIdAndType(reviewId: Long, memberId: Long, type: ReviewLikeType): Boolean
    fun deleteByReviewIdAndMemberIdAndType(reviewId: Long, memberId: Long, type: ReviewLikeType)

    @Query("SELECT rl.review.id FROM ReviewLike rl WHERE rl.review.id IN :reviewIds AND rl.member.id = :memberId AND rl.type = :type")
    fun findReviewIdsByMemberIdAndType(
        @Param("reviewIds") reviewIds: List<Long>,
        @Param("memberId") memberId: Long,
        @Param("type") type: ReviewLikeType
    ): List<Long>

}
