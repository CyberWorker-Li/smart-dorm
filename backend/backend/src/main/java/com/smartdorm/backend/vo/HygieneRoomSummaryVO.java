package com.smartdorm.backend.vo;

import lombok.Data;

import java.util.List;

@Data
public class HygieneRoomSummaryVO {
    private Long roomId;
    private String roomDisplay;
    private int totalTasks;
    private int completedTasks;
    private int overdueTasks;
    private Integer latestScore;
    private Double avgScore;
    private List<HygieneScoreItemVO> recentScores;
}

