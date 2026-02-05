package my.reviewing.reviewing_V2.crawling.repository

import my.reviewing.reviewing_V2.crawling.entity.Category
import my.reviewing.reviewing_V2.crawling.entity.Platform
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long> {
    fun findBySlugAndPlatform(slug: String, platform: Platform): Category?
    fun findByPlatform(platform: Platform): List<Category>
}
