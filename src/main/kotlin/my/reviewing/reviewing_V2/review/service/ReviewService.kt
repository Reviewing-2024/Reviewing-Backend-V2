package my.reviewing.reviewing_V2.review.service

import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.global.slack.SlackService
import my.reviewing.reviewing_V2.global.storage.FileStorageService
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import my.reviewing.reviewing_V2.review.dto.MyReviewResponseDto
import my.reviewing.reviewing_V2.review.dto.ReviewRequestDto
import my.reviewing.reviewing_V2.review.dto.ReviewResponseDto
import my.reviewing.reviewing_V2.review.dto.ReviewSortType
import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewLike
import my.reviewing.reviewing_V2.review.entity.ReviewLikeType
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import my.reviewing.reviewing_V2.review.repository.ReviewLikeRepository
import my.reviewing.reviewing_V2.review.repository.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
@Transactional
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val reviewLikeRepository: ReviewLikeRepository,
    private val courseRepository: CourseRepository,
    private val memberRepository: MemberRepository,
    private val fileStorageService: FileStorageService,
    private val slackService: SlackService
) {

    fun createReview(courseId: UUID, memberId: Long, dto: ReviewRequestDto, certificationFile: MultipartFile) {
        val course = courseRepository.findById(courseId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다.")
        }

        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        // PENDING, APPROVED 상태인 리뷰가 있으면 작성 불가 (REJECTED는 재작성 가능)
        if (reviewRepository.existsByMemberAndCourseAndStateInAndDeletedAtIsNull(
                member, course, listOf(ReviewStateType.PENDING, ReviewStateType.APPROVED)
            )
        ) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 리뷰를 작성하셨습니다.")
        }

        val certificationPath = fileStorageService.saveCertificationFile(certificationFile)

        val review = Review(
            member = member,
            course = course,
            content = dto.content,
            rating = dto.rating,
            certification = certificationPath
        )
        reviewRepository.save(review)
        courseRepository.incrementComments(courseId)
        slackService.sendMessageToSlack(review)
    }

    fun checkBeforeCreateReview(courseId: UUID, memberId: Long) {
        val course = courseRepository.findById(courseId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다.")
        }

        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        if (reviewRepository.existsByMemberAndCourseAndStateInAndDeletedAtIsNull(
                member, course, listOf(ReviewStateType.PENDING, ReviewStateType.APPROVED)
            )
        ) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 리뷰를 작성하셨습니다.")
        }
    }

    @Transactional(readOnly = true)
    fun findReviewsByCourse(
        courseId: UUID,
        sort: ReviewSortType,
        page: Int,
        size: Int,
        memberId: Long?
    ): Slice<ReviewResponseDto> {
        val course = courseRepository.findById(courseId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다.")
        }

        val pageable = PageRequest.of(page, size, sortBy(sort))
        val reviewSlice = reviewRepository.findByCourseAndStateAndDeletedAtIsNull(course, ReviewStateType.APPROVED, pageable)

        if (memberId == null) {
            return reviewSlice.map { review: Review -> ReviewResponseDto.from(review) }
        }

        val reviewIds = reviewSlice.content.map { it.id!! }
        val likedIds = reviewLikeRepository.findReviewIdsByMemberIdAndType(reviewIds, memberId, ReviewLikeType.LIKE).toSet()
        val dislikedIds = reviewLikeRepository.findReviewIdsByMemberIdAndType(reviewIds, memberId, ReviewLikeType.DISLIKE).toSet()

        return reviewSlice.map { review: Review ->
            ReviewResponseDto.from(
                review,
                liked = likedIds.contains(review.id),
                disliked = dislikedIds.contains(review.id)
            )
        }
    }

    private fun sortBy(sort: ReviewSortType): Sort = when (sort) {
        ReviewSortType.LATEST      -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"))
        ReviewSortType.HIGH_RATING -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.ASC, "id"))
        ReviewSortType.LOW_RATING  -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.ASC, "id"))
    }

    @Transactional(readOnly = true)
    fun findMyReviews(memberId: Long, state: ReviewStateType?, page: Int, size: Int): Slice<MyReviewResponseDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        val reviews = if (state == null) {
            reviewRepository.findByMemberIdAndDeletedAtIsNull(memberId, pageable)
        } else {
            reviewRepository.findByMemberIdAndStateAndDeletedAtIsNull(memberId, state, pageable)
        }
        return reviews.map { MyReviewResponseDto.from(it) }
    }

    fun deleteReview(reviewId: Long, memberId: Long) {
        val review = reviewRepository.findById(reviewId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "리뷰를 찾을 수 없습니다.")
        }
        if (review.member.id != memberId) {
            throw BusinessException(ErrorCode.FORBIDDEN, "본인의 리뷰만 삭제할 수 있습니다.")
        }
        if (review.deletedAt != null) {
            throw BusinessException(ErrorCode.NOT_FOUND, "이미 삭제된 리뷰입니다.")
        }
        review.deletedAt = java.time.Instant.now()
        courseRepository.decrementComments(review.course.id!!)
    }

    fun addLike(reviewId: Long, memberId: Long) {
        if (reviewLikeRepository.existsByReviewIdAndMemberIdAndType(reviewId, memberId, ReviewLikeType.LIKE)) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 좋아요를 눌렀습니다.")
        }
        val review = reviewRepository.findById(reviewId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "리뷰를 찾을 수 없습니다.")
        }
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        reviewLikeRepository.save(ReviewLike(review = review, member = member, type = ReviewLikeType.LIKE))
        reviewRepository.incrementLikes(reviewId)
    }

    fun removeLike(reviewId: Long, memberId: Long) {
        if (!reviewLikeRepository.existsByReviewIdAndMemberIdAndType(reviewId, memberId, ReviewLikeType.LIKE)) {
            throw BusinessException(ErrorCode.NOT_FOUND, "좋아요 기록이 없습니다.")
        }
        reviewLikeRepository.deleteByReviewIdAndMemberIdAndType(reviewId, memberId, ReviewLikeType.LIKE)
        reviewRepository.decrementLikes(reviewId)
    }

    fun addDislike(reviewId: Long, memberId: Long) {
        if (reviewLikeRepository.existsByReviewIdAndMemberIdAndType(reviewId, memberId, ReviewLikeType.DISLIKE)) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 싫어요를 눌렀습니다.")
        }
        val review = reviewRepository.findById(reviewId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "리뷰를 찾을 수 없습니다.")
        }
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        reviewLikeRepository.save(ReviewLike(review = review, member = member, type = ReviewLikeType.DISLIKE))
        reviewRepository.incrementDislikes(reviewId)
    }

    fun removeDislike(reviewId: Long, memberId: Long) {
        if (!reviewLikeRepository.existsByReviewIdAndMemberIdAndType(reviewId, memberId, ReviewLikeType.DISLIKE)) {
            throw BusinessException(ErrorCode.NOT_FOUND, "싫어요 기록이 없습니다.")
        }
        reviewLikeRepository.deleteByReviewIdAndMemberIdAndType(reviewId, memberId, ReviewLikeType.DISLIKE)
        reviewRepository.decrementDislikes(reviewId)
    }


}
