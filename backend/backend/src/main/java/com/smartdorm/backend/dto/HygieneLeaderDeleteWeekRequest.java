package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HygieneLeaderDeleteWeekRequest {
    @NotNull
    private Long leaderId;

    @NotBlank
    private String weekStart;
}
