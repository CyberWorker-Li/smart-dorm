package com.smartdorm.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HygieneInspectRequest {
    @NotNull
    private Long managerId;

    @NotNull
    private Long roomId;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer score;

    private String reason;
    private String scoreDate;
}

