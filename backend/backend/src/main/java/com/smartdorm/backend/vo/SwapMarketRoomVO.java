package com.smartdorm.backend.vo;

import lombok.Data;

import java.util.List;

@Data
public class SwapMarketRoomVO {

    private Long batchId;
    private Long roomId;

    private String buildingName;
    private Integer floorNo;
    private Integer roomNo;
    private String gender;

    private Integer capacity;
    private Integer occupied;
    private Integer vacancies;

    private Boolean hasSwapIntent;
    private List<SwapMarketUserIntentVO> intents;
}

