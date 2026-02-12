package my.reviewing.reviewing_V2.course.service

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.JPAExpressions
import org.springframework.transaction.annotation.Transactional
import my.reviewing.reviewing_V2.course.dto.CourseResponseDto
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.QCourse
import my.reviewing.reviewing_V2.crawling.entity.QSubCategoryCourse
import my.reviewing.reviewing_V2.crawling.repository.CourseRepository
import my.reviewing.reviewing_V2.global.api.ApiResponse
import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class CourseService(
    private val courseRepository: CourseRepository
) {

    @Transactional(readOnly = true)
    fun getCourses(
        platform: String?,
        category: String?,
        subCategories: List<String>?,
        sort: String,
        page: Int,
        size: Int
    ): ApiResponse<Page<CourseResponseDto>> {
        val qCourse = QCourse.course
        val builder = BooleanBuilder()

        // 플랫폼 필터 (단일) — Course.platform.englishName
        if (platform != null) {
            builder.and(qCourse.platform.englishName.eq(platform))
        }

        // 카테고리 필터 (단일) — EXISTS subquery: SubCategoryCourse → SubCategory → Category
        if (category != null) {
            val scc = QSubCategoryCourse("scc1")
            builder.and(
                JPAExpressions.selectOne()
                    .from(scc)
                    .where(
                        scc.course.eq(qCourse),
                        scc.subCategory.category.slug.eq(category)
                    ).exists()
            )
        }

        // 서브카테고리 필터 (복수, OR) — EXISTS subquery: SubCategoryCourse → SubCategory
        if (!subCategories.isNullOrEmpty()) {
            val scc = QSubCategoryCourse("scc2")
            builder.and(
                JPAExpressions.selectOne()
                    .from(scc)
                    .where(
                        scc.course.eq(qCourse),
                        scc.subCategory.slug.`in`(subCategories)
                    ).exists()
            )
        }

        // 정렬: rating(평가점수), reviews(리뷰수), 기본=createdAt — 모두 DESC
        val sortBy = when (sort) {
            "rating" -> "rating"
            "reviews" -> "comments"
            else -> "createdAt"
        }
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy))

        val coursePage = courseRepository.findAll(builder, pageable)

        return ApiResponse.ok(coursePage.map { course: Course ->
            CourseResponseDto(
                id = course.id!!,
                slug = course.slug,
                title = course.title,
                teacher = course.teacher ?: "",
                thumbnailImage = course.thumbnailImage,
                thumbnailVideo = course.thumbnailVideo,
                url = course.url,
                rating = course.rating,
                wishes = course.wishes,
                comments = course.comments,
                platform = course.platform.englishName
            )
        })
    }

    fun getCourse(platform: String, slug: String): ApiResponse<CourseResponseDto> {

        val findCourse = courseRepository.findByPlatformEnglishNameAndSlug(platform,slug)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "강의가 존재하지 않습니다.")

        return ApiResponse.ok(
            CourseResponseDto(
                id = findCourse.id!!,
                slug = findCourse.slug,
                title = findCourse.title,
                teacher = findCourse.teacher ?: "",
                thumbnailImage = findCourse.thumbnailImage,
                thumbnailVideo = findCourse.thumbnailVideo,
                url = findCourse.url,
                rating = findCourse.rating,
                wishes = findCourse.wishes,
                comments = findCourse.comments,
                platform = findCourse.platform.englishName
            )
        )

    }


}