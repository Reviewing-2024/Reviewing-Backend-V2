package my.reviewing.reviewing_V2.crawling.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "sub_categories_courses")
class SubCategoryCourse(

    @ManyToOne(fetch = FetchType.LAZY)
    val course: Course,

    @ManyToOne(fetch = FetchType.LAZY)
    val subCategory: SubCategory

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

}