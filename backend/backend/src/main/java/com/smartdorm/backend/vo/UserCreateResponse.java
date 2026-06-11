package com.smartdorm.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserCreateResponse {

    private Long id;
    private String username;
    private String realName;
    private String userNo;
    private String userType;
    private String gender;
    private Boolean leader;
    /** 仅学生有值 */
    private String academicYear;
    private Boolean enabled;
}