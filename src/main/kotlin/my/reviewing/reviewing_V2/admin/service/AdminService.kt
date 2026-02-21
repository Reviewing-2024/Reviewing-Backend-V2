package my.reviewing.reviewing_V2.admin.service

import my.reviewing.reviewing_V2.admin.dto.AdminReviewListResponseDto
import my.reviewing.reviewing_V2.admin.dto.AdminReviewResponseDto
import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import my.reviewing.reviewing_V2.review.repository.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminService(
    private val reviewRepository: ReviewRepository
) {

    @Transactional(readOnly = true)
    fun findReviewsByState(
        state: ReviewStateType,
        page: Int,
        size: Int
    ): AdminReviewListResponseDto {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val reviews = reviewRepository.findByState(state, pageable)
            .map { review: Review -> AdminReviewResponseDto.from(review) }
        val pendingCount = reviewRepository.countByState(ReviewStateType.PENDING)
        return AdminReviewListResponseDto(reviews = reviews, pendingCount = pendingCount)
    }

}