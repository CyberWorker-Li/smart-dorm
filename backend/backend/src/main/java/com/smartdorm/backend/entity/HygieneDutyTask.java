package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dorm_hygiene_duty_task")
@EqualsAndHashCode(callSuper = true)
public class HygieneDutyTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private LocalDate dutyDate;

    private Long dutyUserId;

    private String dutyItem;

    private String status;

    private LocalDateTime deadlineTime;

    private Long createdBy;
}
