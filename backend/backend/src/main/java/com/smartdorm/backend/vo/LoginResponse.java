package com.smartdorm.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String userType;
    private Boolean leader;
    private String gender;
    private String targetPortal;
}