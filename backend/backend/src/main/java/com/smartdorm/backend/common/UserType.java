package com.smartdorm.backend.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserType {

    STUDENT("STUDENT_LEADER"),
    DORM_MANAGER("DORM_MANAGER"),
    SYS_ADMIN("SYS_ADMIN");

    private final String portal;

    public static UserType fromCode(String code) {
        for (UserType value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new BusinessException(400, "用户角色类型非法");
    }
}