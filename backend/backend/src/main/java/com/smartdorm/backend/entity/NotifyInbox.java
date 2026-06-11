package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("notify_inbox")
@EqualsAndHashCode(callSuper = true)
public class NotifyInbox extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recipientUserId;

    private String bizType;

    private Long bizId;

    private String title;

    private String summary;

    private LocalDateTime readTime;
}

