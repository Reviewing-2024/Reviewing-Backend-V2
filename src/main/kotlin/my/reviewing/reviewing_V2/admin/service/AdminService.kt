package my.reviewing.reviewing_V2.admin.service

import my.reviewing.reviewing_V2.admin.dto.AdminReviewListResponseDto
import my.reviewing.reviewing_V2.admin.dto.AdminReviewResponseDto
import my.reviewing.reviewing_V2.admin.dto.ReviewRejectRequestDto
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.review.repository.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminService(
    private val reviewRepository: ReviewRepository,
    private val courseRepository: CourseRepository
) {

    @Transactional(readOnly = true)
    fun findReviewsByState(
        state: ReviewStateType,
        page: Int,
        size: Int
    ): AdminReviewListResponseDto {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        val reviews = reviewRepository.findByStateAndDeletedAtIsNull(state, pageable)
            .map { review: Review -> AdminReviewResponseDto.from(review) }
        val pendingCount = reviewRepository.countByStateAndDeletedAtIsNull(ReviewStateType.PENDING)
        return AdminReviewListResponseDto(reviews = reviews, pendingCount = pendingCount)
    }

    @Transactional
    fun changeReviewApprove(reviewId: Long) {
        val review = reviewRepository.findById(reviewId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "리뷰가 없습니다.")
        }
        review.state = ReviewStateType.APPROVED
        courseRepository.updateRatingByAverage(review.course.id!!)
    }

    @Transactional
    fun changeReviewReject(reviewId: Long, reviewRejectRequestDto: ReviewRejectRequestDto) {
        val review = reviewRepository.findById(reviewId).orElseThrow{
            BusinessException(ErrorCode.NOT_FOUND, "리뷰가 없습니다.")
        }
        review.state = ReviewStateType.REJECTED
        review.rejectionReason = reviewRejectRequestDto.rejectionReason
    }

}