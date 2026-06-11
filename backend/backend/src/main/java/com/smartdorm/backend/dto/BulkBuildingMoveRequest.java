package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BulkBuildingMoveRequest {

    @NotNull
    private Long batchId;

    @NotBlank
    private String academicYear;

    /** MALE / FEMALE */
    @NotBlank
    private String gender;

    @NotNull
    private Long sourceBuildingId;

    @NotNull
    private Long targetBuildingId;
}
