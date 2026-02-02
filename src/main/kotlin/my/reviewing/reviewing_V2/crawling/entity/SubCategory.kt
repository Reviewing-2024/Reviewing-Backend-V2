package my.reviewing.reviewing_V2.crawling.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "sub_categories")
class SubCategory(

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val slug: String,

    @ManyToOne(fetch = FetchType.LAZY)
    val category: Category

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

}