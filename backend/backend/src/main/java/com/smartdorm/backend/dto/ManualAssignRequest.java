package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualAssignRequest {

    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotNull(message = "目标房间ID不能为空")
    private Long roomId;

    private Integer bedNo;
}
