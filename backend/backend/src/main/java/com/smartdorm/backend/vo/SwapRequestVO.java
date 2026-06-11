package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SwapRequestVO {

    private Long id;
    private Long batchId;

    // Student A info
    private Long studentAId;
    private String studentAName;
    private String studentANo;
    private String studentABuilding;
    private String studentARoomNo;

    // Student B info
    private Long studentBId;
    private String studentBName;
    private String studentBNo;
    private String studentBBuilding;
    private String studentBRoomNo;

    // Request details
    private String initiatorRemark;
    private String status;
    private String bConfirmStatus;
    private String handleRemark;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
