package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InsertRoomRequest {

    @NotNull
    private Long floorId;

    @NotNull
    private Integer roomNo;

    @NotNull
    private Integer capacity;

    private String building;

    private Boolean mainValid = true;
}
