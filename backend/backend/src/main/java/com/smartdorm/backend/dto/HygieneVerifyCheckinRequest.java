package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HygieneVerifyCheckinRequest {
    @NotNull
    private Long managerId;

    @NotNull
    private Long checkinId;

    @NotBlank
    private String verifyStatus;
}

