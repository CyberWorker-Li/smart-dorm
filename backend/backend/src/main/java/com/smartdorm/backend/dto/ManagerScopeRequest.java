package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManagerScopeRequest {

    @NotNull
    private Long managerId;

    private Long buildingId;

    private Long floorId;
}
