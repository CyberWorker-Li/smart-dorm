package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitSwapRequest {

    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    @NotNull(message = "学生A（发起人）ID不能为空")
    private Long studentAId;

    @NotNull(message = "学生B（互换目标）ID不能为空")
    private Long studentBId;

    private String remark;
}
