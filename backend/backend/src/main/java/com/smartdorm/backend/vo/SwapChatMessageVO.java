package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SwapChatMessageVO {

    private Long id;
    private Boolean mine;
    private String content;
    private LocalDateTime time;
}

