package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRepairRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotNull(message = "故障类型不能为空")
    private String type;

    private String detail;
}

