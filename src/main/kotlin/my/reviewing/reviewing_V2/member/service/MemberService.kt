package my.reviewing.reviewing_V2.member.service

import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class MemberService(
    private val memberRepository: MemberRepository
) {

    @Transactional
    fun updateNickname(memberId: Long, nickname: String) {
        if (memberRepository.existsByName(nickname)) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다.")
        }
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        member.name = nickname
    }

    @Transactional
    fun updateProfileImage(memberId: Long, file: MultipartFile) {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        member.profileImage = saveProfileFile(memberId, file)
    }

    private fun saveProfileFile(memberId: Long, file: MultipartFile): String {
        val uploadDir = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "resources", "static", "profiles", memberId.toString()
        )
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir)
        }
        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        Files.write(uploadDir.resolve(fileName), file.bytes)
        return "/profiles/$memberId/$fileName"
    }

    @Transactional
    fun grantAdmin(memberId: Long) {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        member.role = "ROLE_ADMIN"
    }
}
