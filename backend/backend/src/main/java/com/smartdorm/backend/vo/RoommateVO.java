package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class RoommateVO {

    private Long studentId;
    private String realName;
    private String userNo;
    /** MALE / FEMALE */
    private String gender;
    /** 脱敏：仅展示非隐私字段 */
    private String wakeUpTime;
    private String sleepTime;
    private String personality;
    private String hobbies;
    private String hometown;
    private Integer bedNo;
}
