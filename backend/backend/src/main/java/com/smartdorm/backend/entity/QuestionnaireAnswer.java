package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_questionnaire_answer")
@EqualsAndHashCode(callSuper = false)
public class QuestionnaireAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionnaireId;

    private Long studentId;

    private String wakeUpTime;

    private String sleepTime;

    private Boolean stayUpLate;

    private Boolean smoke;

    private Boolean keepClean;

    private String hometown;

    private String major;

    /** INTROVERT / EXTROVERT / AMBIVERT */
    private String personality;

    private String hobbies;

    private String selfDescription;

    private String dormExpectation;

    /** SUBMITTED / RECALLED */
    private String status;

    private LocalDateTime submitTime;

    private LocalDateTime updateTime;
}
