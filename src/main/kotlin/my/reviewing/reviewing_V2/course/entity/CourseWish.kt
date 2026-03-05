package my.reviewing.reviewing_V2.course.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import my.reviewing.reviewing_V2.crawling.entity.Course
import my.reviewing.reviewing_V2.member.entity.Member

@Entity
@Table(
    name = "courses_wishes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["course_id", "member_id"])]
)
class CourseWish(

    @ManyToOne(fetch = FetchType.LAZY)
    val course: Course,

    @ManyToOne(fetch = FetchType.LAZY)
    val member: Member
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

}