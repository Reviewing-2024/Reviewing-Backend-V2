package my.reviewing.reviewing_V2.crawling.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "categories_courses",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["course_id", "category_id"])
    ]
    )
class CategoryCourse (

    @ManyToOne(fetch = FetchType.LAZY)
    val course: Course,

    @ManyToOne(fetch = FetchType.LAZY)
    val category: Category

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

}