package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBatchRequest {

    @NotNull(message = "问卷ID不能为空")
    private Long questionnaireId;

    @NotBlank(message = "学年不能为空")
    private String academicYear;

    private String remark;
}
