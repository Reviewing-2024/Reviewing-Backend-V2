package my.reviewing.reviewing_V2.review.service

import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import my.reviewing.reviewing_V2.review.dto.ReviewRequestDto
import my.reviewing.reviewing_V2.review.dto.ReviewResponseDto
import my.reviewing.reviewing_V2.review.dto.ReviewSortType
import my.reviewing.reviewing_V2.review.entity.Review
import my.reviewing.reviewing_V2.review.entity.ReviewStateType
import my.reviewing.reviewing_V2.review.repository.ReviewRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
@Transactional
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val courseRepository: CourseRepository,
    private val memberRepository: MemberRepository
) {

    fun createReview(courseId: UUID, memberId: Long, dto: ReviewRequestDto, certificationFile: MultipartFile) {
        val course = courseRepository.findById(courseId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다.")
        }

        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }

        // PENDING, APPROVED 상태인 리뷰가 있으면 작성 불가 (REJECTED는 재작성 가능)
        if (reviewRepository.existsByMemberAndCourseAndStateIn(
                member, course, listOf(ReviewStateType.PENDING, ReviewStateType.APPROVED)
            )
        ) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 리뷰를 작성하셨습니다.")
        }

        val certificationPath = saveCertificationFile(certificationFile)

        val review = Review(
            member = member,
            course = course,
            content = dto.content,
            rating = dto.rating,
            certification = certificationPath
        )
        reviewRepository.save(review)
    }

    @Transactional(readOnly = true)
    fun findReviewsByCourse(
        courseId: UUID,
        sort: ReviewSortType,
        page: Int,
        size: Int
    ): Slice<ReviewResponseDto> {
        val course = courseRepository.findById(courseId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "강의를 찾을 수 없습니다.")
        }

        val pageable = PageRequest.of(page, size, sortBy(sort))

        return reviewRepository.findByCourseAndState(course, ReviewStateType.APPROVED, pageable)
            .map { review: Review -> ReviewResponseDto.from(review) }
    }

    private fun sortBy(sort: ReviewSortType): Sort = when (sort) {
        ReviewSortType.LATEST      -> Sort.by(Sort.Direction.DESC, "createdAt")
        ReviewSortType.HIGH_RATING -> Sort.by(Sort.Direction.DESC, "rating")
        ReviewSortType.LOW_RATING  -> Sort.by(Sort.Direction.ASC, "rating")
    }

    private fun saveCertificationFile(file: MultipartFile): String {
        val uploadDir = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "resources", "static", "certifications"
        )

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir)
        }

        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        Files.write(uploadDir.resolve(fileName), file.bytes)

        return "/certifications/$fileName"
    }
}
