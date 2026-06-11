package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HygieneCheckinRequest {
    @NotNull
    private Long taskId;

    @NotNull
    private Long studentId;

    @NotBlank
    private String photoUrl;

    private String locationText;

    private String remark;
}

