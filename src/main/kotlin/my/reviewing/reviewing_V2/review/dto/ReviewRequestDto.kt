package my.reviewing.reviewing_V2.review.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

class ReviewRequestDto(

    @NotNull(message = "Rating은 null일 수 없습니다.")
    @DecimalMin(value = "1.0", message = "Rating은 1.0 이상이어야 합니다.")
    @DecimalMax(value = "5.0", message = "Rating은 5.0 이하이어야 합니다.")
    val rating: BigDecimal,

    @NotBlank(message = "Content는 비어 있을 수 없습니다.")
    val content: String

)