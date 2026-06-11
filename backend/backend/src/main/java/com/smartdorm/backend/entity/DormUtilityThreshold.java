package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_utility_threshold")
@EqualsAndHashCode(callSuper = true)
public class DormUtilityThreshold extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Double waterLimit;

    private Double powerLimit;
}

