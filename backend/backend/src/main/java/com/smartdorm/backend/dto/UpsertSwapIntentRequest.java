package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertSwapIntentRequest {

    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    private String remark;
}
