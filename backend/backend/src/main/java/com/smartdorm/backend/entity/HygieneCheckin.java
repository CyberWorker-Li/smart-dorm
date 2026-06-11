package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_hygiene_checkin")
@EqualsAndHashCode(callSuper = true)
public class HygieneCheckin extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userId;

    private String photoUrl;

    private String locationText;

    private String remark;

    private LocalDateTime checkinTime;

    private String verifyStatus;

    private Long verifiedBy;

    private LocalDateTime verifiedTime;
}
