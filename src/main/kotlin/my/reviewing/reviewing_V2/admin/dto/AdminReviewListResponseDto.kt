package my.reviewing.reviewing_V2.admin.dto

import org.springframework.data.domain.Slice

class AdminReviewListResponseDto(
    val reviews: Slice<AdminReviewResponseDto>,
    val pendingCount: Long
)
