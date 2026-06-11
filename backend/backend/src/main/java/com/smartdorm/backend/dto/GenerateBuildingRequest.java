package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateBuildingRequest {

    @NotBlank(message = "楼房名称不能为空")
    private String name;

    @NotBlank(message = "楼房性别不能为空")
    private String gender;

    /** 可空：总层数 */
    private Integer totalFloors;

    /** 可空：每层房间数；若填则必须已填层数 */
    private Integer roomsPerFloor;
}
