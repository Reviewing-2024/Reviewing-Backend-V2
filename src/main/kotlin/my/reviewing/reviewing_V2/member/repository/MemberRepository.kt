package my.reviewing.reviewing_V2.member.repository

import my.reviewing.reviewing_V2.member.entity.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {

    fun findByUsername(username: String): Member?

    fun existsByName(name: String): Boolean

}