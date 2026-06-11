package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PeerEvalManagerDetailVO {
    private Long evalId;
    private Long batchId;
    private Long roomId;
    private String roomDisplay;
    private String month;
    private Long submitterStudentId;
    private String submitterName;
    private LocalDateTime createTime;
    private List<PeerEvalManagerDetailItemVO> items;
}

