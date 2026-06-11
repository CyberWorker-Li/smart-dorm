package com.smartdorm.backend.assignment.algorithm;

import com.smartdorm.backend.entity.QuestionnaireAnswer;
import com.smartdorm.backend.entity.User;
import lombok.Data;

/**
 * 携带问卷答案和用户信息的学生对象，用于分配算法
 */
@Data
public class StudentWithAnswer {
    private Long studentId;
    private QuestionnaireAnswer answer;
    private User user;          // 包含性别、真实姓名等
}