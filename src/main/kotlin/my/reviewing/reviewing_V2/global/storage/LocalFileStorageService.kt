package my.reviewing.reviewing_V2.global.storage

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
@Profile("!prod")
class LocalFileStorageService : FileStorageService {

    override fun saveProfileImage(memberId: Long, file: MultipartFile): String {
        val uploadDir = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "resources", "static", "profiles", memberId.toString()
        )
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir)
        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        Files.write(uploadDir.resolve(fileName), file.bytes)
        return "/profiles/$memberId/$fileName"
    }

    override fun saveCertificationFile(file: MultipartFile): String {
        val uploadDir = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "resources", "static", "certifications"
        )
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir)
        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        Files.write(uploadDir.resolve(fileName), file.bytes)
        return "/certifications/$fileName"
    }
}