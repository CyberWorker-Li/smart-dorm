package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PeerEvalManagerListItemVO {
    private Long evalId;
    private Long batchId;
    private Long roomId;
    private String roomDisplay;
    private String month;
    private Long submitterStudentId;
    private String submitterName;
    private LocalDateTime createTime;
    private Boolean lowRisk;
    private Integer lowItemCount;
}

