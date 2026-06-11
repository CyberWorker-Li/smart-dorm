package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateNoiseRequest {

    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    @NotNull(message = "发起人学生ID不能为空")
    private Long fromStudentId;

    @NotNull(message = "目标宿舍ID不能为空")
    private Long toRoomId;

    @NotBlank(message = "提醒内容不能为空")
    private String content;
}

