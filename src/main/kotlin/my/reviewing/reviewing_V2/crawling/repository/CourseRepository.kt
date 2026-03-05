package my.reviewing.reviewing_V2.crawling.repository

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CourseRepository : JpaRepository<Course, UUID>, QuerydslPredicateExecutor<Course> {

    fun findByPlatformAndSlug(platform: Platform, slug: String): Course?

    fun findByPlatformEnglishNameAndSlug(platform: String, slug: String): Course?

    @Modifying
    @Query("UPDATE Course c SET c.wishes = c.wishes + 1 WHERE c.id = :courseId")
    fun incrementWishes(@Param("courseId") courseId: UUID)

    @Modifying
    @Query("UPDATE Course c SET c.wishes = c.wishes - 1 WHERE c.id = :courseId")
    fun decrementWishes(@Param("courseId") courseId: UUID)

}