package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_utility_monthly")
@EqualsAndHashCode(callSuper = true)
public class DormUtilityMonthly extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String month;

    private Double water;

    private Double power;

    private Double cost;
}

