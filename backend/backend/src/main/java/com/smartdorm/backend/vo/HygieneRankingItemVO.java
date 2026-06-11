package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class HygieneRankingItemVO {
    private int rank;
    private Long roomId;
    private String roomDisplay;
    private Double avgScore;
    private Integer latestScore;
    private String boardType;
}

