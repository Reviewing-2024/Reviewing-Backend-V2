package my.reviewing.reviewing_V2.crawling.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "courses")
@EntityListeners(AuditingEntityListener::class)
class Course (

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val url: String,

    @Column(nullable = true)
    val thumbnailImage: String? = null,

    @Column(nullable = true)
    val thumbnailVideo: String? = null,

    @Column(nullable = true)
    val teacher: String? = null,

    @Column(nullable = false)
    val slug: String,

    @Column(precision = 2, scale = 1, nullable = false)
    val rating: BigDecimal = BigDecimal.valueOf(0.0),

    val wishes: Int = 0,

    val comments: Int = 0,

    val updated: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    val platform: Platform

) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant? = null

}