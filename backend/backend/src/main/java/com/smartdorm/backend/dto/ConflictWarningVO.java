package com.smartdorm.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConflictWarningVO {
    private Long id;
    private Long batchId;
    private Long roomId;
    private String roomDisplay;    // 楼栋-楼层-房间号
    private Long studentId1;
    private String studentName1;
    private Long studentId2;
    private String studentName2;
    private Double conflictScore;
    private String description;
    private String status;
    private LocalDateTime createTime;
}