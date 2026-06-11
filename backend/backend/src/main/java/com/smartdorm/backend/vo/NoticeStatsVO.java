package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class NoticeStatsVO {

    private Long totalRecipients;
    private Long readCount;
    private Long unreadCount;
}

