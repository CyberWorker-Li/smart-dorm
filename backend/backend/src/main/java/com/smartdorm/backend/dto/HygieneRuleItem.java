package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HygieneRuleItem {
    @NotBlank
    private String key;

    @NotBlank
    private String value;

    private String remark;
}

