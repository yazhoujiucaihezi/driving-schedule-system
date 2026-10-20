package com.box.reservation.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AiToolParams(
    @NotBlank(message = "教练姓名不能为空")
    String coachName,
    // 这里用字符串接收，然后在下面手动转 LocalDate 并校验，或者用自定义注解
    @NotBlank(message = "日期不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式必须为 yyyy-MM-dd")
    String dateString,
    String timeSlot
) {
}
