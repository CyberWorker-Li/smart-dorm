package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HygieneReassignRequest {
    @NotNull
    private Long leaderId;

    @NotNull
    private Long taskId;

    @NotNull
    private Long newUserId;
}

