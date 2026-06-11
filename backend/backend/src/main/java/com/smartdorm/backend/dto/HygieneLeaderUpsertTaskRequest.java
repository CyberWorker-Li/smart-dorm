package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HygieneLeaderUpsertTaskRequest {
    @NotNull
    private Long leaderId;

    @NotBlank
    private String dutyDate;

    @NotNull
    private Long dutyUserId;

    @NotBlank
    private String dutyItem;

    private String deadlineTime;
}
