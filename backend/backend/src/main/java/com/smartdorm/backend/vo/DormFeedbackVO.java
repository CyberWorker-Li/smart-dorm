package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DormFeedbackVO {
    private Long id;
    private String content;
    private String status;
    private Boolean canEscalate;
    private LocalDateTime time;
}

