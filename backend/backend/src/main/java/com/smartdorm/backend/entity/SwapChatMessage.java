package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dorm_swap_chat_message")
public class SwapChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long threadId;

    private Long senderId;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
