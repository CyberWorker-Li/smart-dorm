package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoiseRequestVO {

    private Long id;
    private Long batchId;
    private Long fromRoomId;
    private Long toRoomId;
    private String fromRoomDisplay;
    private String toRoomDisplay;
    private String content;
    private String status;
    private LocalDateTime ackTime;
    private LocalDateTime escalateTime;
    private String handlerName;
    private String handleRemark;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}

