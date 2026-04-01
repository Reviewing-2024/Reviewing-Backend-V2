package my.reviewing.reviewing_V2.review.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import my.reviewing.reviewing_V2.member.entity.Member

@Entity
@Table(
    name = "review_likes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["review_id", "member_id", "type"])]
)
class ReviewLike(

    @ManyToOne(fetch = FetchType.LAZY)
    val review: Review,

    @ManyToOne(fetch = FetchType.LAZY)
    val member: Member,

    @Enumerated(EnumType.STRING)
    var type: ReviewLikeType

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

}
