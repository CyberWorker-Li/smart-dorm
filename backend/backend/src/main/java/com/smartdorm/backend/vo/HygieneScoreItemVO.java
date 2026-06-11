package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HygieneScoreItemVO {
    private Long id;
    private Long roomId;
    private LocalDate scoreDate;
    private String periodType;
    private Integer score;
    private String sourceType;
    private String reason;
    private Long inspectorId;
}

