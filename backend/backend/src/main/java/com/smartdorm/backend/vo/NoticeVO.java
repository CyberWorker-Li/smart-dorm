package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeVO {

    private Long id;
    private Long publisherId;
    private String publisherName;
    private String title;
    private String content;
    private String scopeType;
    private Long buildingId;
    private Long floorId;
    private String status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
}

