package my.reviewing.reviewing_V2.crawling.repository

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import java.util.UUID

interface CourseRepository : JpaRepository<Course, UUID>, QuerydslPredicateExecutor<Course> {

    fun findByPlatformAndSlug(platform: Platform, slug: String): Course?

    fun findByPlatformEnglishNameAndSlug(platform: String, slug: String): Course?

}