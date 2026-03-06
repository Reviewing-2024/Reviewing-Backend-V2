package my.reviewing.reviewing_V2.crawling.repository

import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.crawling.entity.SubCategory
import my.reviewing.reviewing_V2.crawling.entity.SubCategoryCourse
import org.springframework.data.jpa.repository.JpaRepository

interface SubCategoryCourseRepository : JpaRepository<SubCategoryCourse, Long> {
    fun findByCourseAndSubCategory(course: Course, subCategory: SubCategory): SubCategoryCourse?
    fun findByCourse(course: Course): List<SubCategoryCourse>
}
