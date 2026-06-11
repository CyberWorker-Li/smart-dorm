package com.smartdorm.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SubmitPeerEvalRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "月份不能为空")
    private String month;

    @Valid
    @NotNull(message = "互评明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "被评学生ID不能为空")
        private Long targetStudentId;
        @NotNull(message = "作息配合度不能为空")
        @Min(value = 1, message = "作息配合度范围 1-10")
        @Max(value = 10, message = "作息配合度范围 1-10")
        private Integer schedule;
        @NotNull(message = "卫生贡献不能为空")
        @Min(value = 1, message = "卫生贡献范围 1-10")
        @Max(value = 10, message = "卫生贡献范围 1-10")
        private Integer hygiene;
        @NotNull(message = "沟通友好度不能为空")
        @Min(value = 1, message = "沟通友好度范围 1-10")
        @Max(value = 10, message = "沟通友好度范围 1-10")
        private Integer communication;
    }
}

