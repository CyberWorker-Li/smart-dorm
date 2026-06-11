package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class ActivityVO {
    private Long id;
    private String name;
    private String status;
    private Boolean joined;
}

