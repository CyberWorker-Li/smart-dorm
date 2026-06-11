package com.smartdorm.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class HygieneUpsertRulesRequest {
    @NotNull
    private Long adminId;

    @Valid
    @NotEmpty
    private List<HygieneRuleItem> items;
}

