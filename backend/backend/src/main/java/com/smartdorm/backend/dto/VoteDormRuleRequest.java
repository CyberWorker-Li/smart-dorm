package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteDormRuleRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotNull(message = "投票选项不能为空")
    private Boolean agree;
}

