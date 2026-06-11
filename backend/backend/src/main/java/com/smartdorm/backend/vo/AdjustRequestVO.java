package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdjustRequestVO {

    private Long id;
    private Long batchId;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String currentBuilding;
    private String currentRoomNo;
    private String targetBuilding;
    private String targetRoomNo;
    private String reason;
    private String status;
    private String handleRemark;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
