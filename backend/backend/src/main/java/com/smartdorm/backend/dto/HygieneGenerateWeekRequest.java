package com.smartdorm.backend.dto;

import lombok.Data;

@Data
public class HygieneGenerateWeekRequest {
    private Long leaderId;
    private String weekStart;
    private String dutyItem;
}

