package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DormRuleVO {
    private Long id;
    private String content;
    private String status;
    private Integer approvalRate;
    private Boolean voted;
    private LocalDateTime createTime;
}

