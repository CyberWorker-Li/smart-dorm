package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class DormPublicRoomVO {

    private Long batchId;
    private Long roomId;
    private String buildingName;
    private Integer floorNo;
    private Integer roomNo;
    private String gender;
    private Integer capacity;
    private Integer occupied;
    private Integer vacancies;
    private Double hygieneAvg;
    private String sleepTag;
    private String wakeTag;
    private Boolean hasSwapIntent;
    private Integer swapIntentCount;
}

