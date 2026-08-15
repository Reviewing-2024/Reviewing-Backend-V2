package my.reviewing.reviewing_V2.global.storage

import org.springframework.web.multipart.MultipartFile

interface FileStorageService {
    fun saveProfileImage(memberId: Long, file: MultipartFile): String
    fun saveCertificationFile(file: MultipartFile): String
}
