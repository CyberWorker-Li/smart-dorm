package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveUtilityThresholdRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    private Double waterLimit;

    private Double powerLimit;
}

