package my.reviewing.reviewing_V2.member.service

import my.reviewing.reviewing_V2.global.error.BusinessException
import my.reviewing.reviewing_V2.global.error.ErrorCode
import my.reviewing.reviewing_V2.global.storage.FileStorageService
import my.reviewing.reviewing_V2.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val fileStorageService: FileStorageService
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
        member.profileImage = fileStorageService.saveProfileImage(memberId, file)
    }

    @Transactional
    fun updateProfile(memberId: Long, nickname: String?, file: MultipartFile?) {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        if (nickname != null) {
            if (memberRepository.existsByName(nickname)) {
                throw BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다.")
            }
            member.name = nickname
        }
        if (file != null) {
            member.profileImage = fileStorageService.saveProfileImage(memberId, file)
        }
    }

    @Transactional
    fun grantAdmin(memberId: Long) {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        member.role = "ROLE_ADMIN"
    }
}
