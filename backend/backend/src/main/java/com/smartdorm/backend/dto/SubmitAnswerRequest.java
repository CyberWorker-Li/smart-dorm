package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitAnswerRequest {

    @NotNull(message = "问卷ID不能为空")
    private Long questionnaireId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    private String wakeUpTime;

    private String sleepTime;

    private Boolean stayUpLate = false;

    private Boolean smoke = false;

    private Boolean keepClean = true;

    private String hometown;

    private String major;

    /** INTROVERT / EXTROVERT / AMBIVERT */
    private String personality;

    private String hobbies;

    private String selfDescription;

    private String dormExpectation;
}
