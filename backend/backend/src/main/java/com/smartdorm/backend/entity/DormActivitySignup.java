package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_activity_signup")
@EqualsAndHashCode(callSuper = true)
public class DormActivitySignup extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long roomId;

    private Long signupStudentId;
}

