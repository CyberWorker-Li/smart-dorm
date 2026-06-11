package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyInboxVO {

    private Long inboxId;
    private String bizType;
    private Long bizId;
    private String title;
    private String summary;
    private LocalDateTime readTime;
    private LocalDateTime time;
}

