package my.reviewing.reviewing_V2.global.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
@Profile("prod")
class S3FileStorageService(
    private val s3Client: S3Client,
    @Value("\${cloud.aws.s3.bucket}") private val bucket: String,
    @Value("\${cloud.aws.s3.base-url}") private val baseUrl: String
) : FileStorageService {

    override fun saveProfileImage(memberId: Long, file: MultipartFile): String {
        val ext = file.originalFilename?.substringAfterLast('.', "") ?: ""
        val key = "profiles/$memberId/${UUID.randomUUID()}${if (ext.isNotEmpty()) ".$ext" else ""}"
        upload(key, file)
        return "$baseUrl/$key"
    }

    override fun saveCertificationFile(file: MultipartFile): String {
        val ext = file.originalFilename?.substringAfterLast('.', "") ?: ""
        val key = "certifications/${UUID.randomUUID()}${if (ext.isNotEmpty()) ".$ext" else ""}"
        upload(key, file)
        return "$baseUrl/$key"
    }

    private fun upload(key: String, file: MultipartFile) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.contentType)
                .build(),
            RequestBody.fromBytes(file.bytes)
        )
    }
}