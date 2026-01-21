package my.reviewing.reviewing_V2.member.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "members")
@EntityListeners(AuditingEntityListener::class)
class Member(
    @Column(unique = true, nullable = false)
    val username: String,

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, length = 20)
    var role: String
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