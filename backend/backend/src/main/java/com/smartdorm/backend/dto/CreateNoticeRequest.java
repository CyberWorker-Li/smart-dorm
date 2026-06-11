package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateNoticeRequest {

    @NotNull(message = "发布人ID不能为空")
    private Long publisherId;

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @NotBlank(message = "范围类型不能为空")
    private String scopeType;

    private Long buildingId;

    private Long floorId;
}

