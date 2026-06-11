package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDormFeedbackRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "反馈内容不能为空")
    private String content;
}

