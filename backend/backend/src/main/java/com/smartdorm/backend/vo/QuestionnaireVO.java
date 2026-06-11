package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionnaireVO {

    private Long id;
    private String title;
    private String academicYear;
    private String status;
    private LocalDateTime deadline;
    private LocalDateTime createTime;
    /** 已提交答卷数量 */
    private Long submittedCount;
    /** 在册学生总数（用于回收提醒） */
    private Long totalStudents;
    /** 未提交人数 */
    private Long pendingCount;
}
