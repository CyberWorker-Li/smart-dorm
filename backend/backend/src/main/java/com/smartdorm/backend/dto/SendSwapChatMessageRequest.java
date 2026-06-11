package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendSwapChatMessageRequest {

    @NotNull(message = "会话ID不能为空")
    private Long threadId;

    @NotNull(message = "发送者学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
