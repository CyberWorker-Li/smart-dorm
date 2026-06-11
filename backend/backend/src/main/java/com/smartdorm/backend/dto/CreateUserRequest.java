package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String userNo;

    @NotBlank(message = "角色类型不能为空")
    private String userType;

    /** MALE / FEMALE */
    @NotBlank(message = "性别不能为空")
    private String gender;

    private Boolean leader = false;

    /** 学年，如 2025-2026；创建学生账号时必填 */
    private String academicYear;
}