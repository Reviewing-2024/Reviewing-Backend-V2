package my.reviewing.reviewing_V2.crawling.repository

import my.reviewing.reviewing_V2.crawling.entity.Category
import my.reviewing.reviewing_V2.crawling.entity.SubCategory
import org.springframework.data.jpa.repository.JpaRepository

interface SubCategoryRepository : JpaRepository<SubCategory, Long> {
    fun findBySlugAndCategoryId(slug: String, categoryId: Long): SubCategory?
    fun findByCategory(category: Category): List<SubCategory>
}
