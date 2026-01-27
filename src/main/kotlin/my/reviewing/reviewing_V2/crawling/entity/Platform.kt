package my.reviewing.reviewing_V2.crawling.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "platforms")
class Platform (

    @Column(unique = true, nullable = false)
    val koreanName: String,

    @Column(unique = true, nullable = false)
    val englishName: String,

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

}