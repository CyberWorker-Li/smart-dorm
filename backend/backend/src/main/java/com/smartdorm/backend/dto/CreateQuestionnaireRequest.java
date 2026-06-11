package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateQuestionnaireRequest {

    @NotBlank(message = "问卷标题不能为空")
    private String title;

    @NotBlank(message = "学年不能为空")
    private String academicYear;

    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;

    /** 若填写，则从该问卷复制已提交的答卷到新问卷（学生再次提交会覆盖对应记录） */
    private Long inheritFromQuestionnaireId;
}
