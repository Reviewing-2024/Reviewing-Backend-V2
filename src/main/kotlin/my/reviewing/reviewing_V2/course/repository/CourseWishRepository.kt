package my.reviewing.reviewing_V2.course.repository

import my.reviewing.reviewing_V2.course.entity.CourseWish
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CourseWishRepository : JpaRepository<CourseWish, Long> {

    fun existsByCourseIdAndMemberId(courseId: UUID, memberId: Long): Boolean
    fun deleteByCourseIdAndMemberId(courseId: UUID, memberId: Long)

    @Query("select cw.course.id from CourseWish cw where cw.course.id in :courseIds and cw.member.id = :memberId")
    fun findWishedCourseIds(@Param("courseIds") courseIds: List<UUID>, @Param("memberId") memberId: Long): List<UUID>

}