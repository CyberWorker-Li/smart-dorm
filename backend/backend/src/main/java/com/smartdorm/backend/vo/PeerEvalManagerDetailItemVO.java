package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class PeerEvalManagerDetailItemVO {
    private Long targetStudentId;
    private String targetName;
    private Integer scheduleScore;
    private Integer hygieneScore;
    private Integer communicationScore;
    private Double avgScore;
    private Boolean low;
}

