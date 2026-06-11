package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SwapChatThreadVO {

    private Long threadId;
    private Long batchId;
    private Long otherStudentId;
    private String otherAlias;
    private String lastMessage;
    private LocalDateTime lastTime;
}

