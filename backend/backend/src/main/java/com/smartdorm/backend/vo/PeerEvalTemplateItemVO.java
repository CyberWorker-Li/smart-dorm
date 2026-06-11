package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class PeerEvalTemplateItemVO {
    private Long targetStudentId;
    private String name;
    private Integer schedule;
    private Integer hygiene;
    private Integer communication;
}

