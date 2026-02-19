package my.reviewing.reviewing_V2.review.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.member.entity.Member
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "reviews")
@EntityListeners(AuditingEntityListener::class)
class Review(

    @ManyToOne(fetch = FetchType.LAZY)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    val course: Course,

    @Column(columnDefinition = "TEXT")
    val content: String,

    @Column(precision = 3, scale = 1)
    val rating: BigDecimal,

    @Column(columnDefinition = "TEXT")
    val certification: String,

    var likes: Int = 0,

    var dislikes: Int = 0,

    var deletedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    var state: ReviewStateType = ReviewStateType.PENDING,

    var rejectionReason: String? = null

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null

}
