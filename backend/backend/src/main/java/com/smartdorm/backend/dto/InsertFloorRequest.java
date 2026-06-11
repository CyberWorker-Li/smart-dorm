package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InsertFloorRequest {

    @NotNull
    private Long buildingId;

    @NotNull
    private Integer floorNo;

    @NotNull
    private Integer maxRooms;

    /** 本条插入的楼层是否有效；捎带插入的更低层为无效 */
    private Boolean mainValid = true;
}
