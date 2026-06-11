package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_hygiene_rule")
@EqualsAndHashCode(callSuper = true)
public class HygieneRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleKey;

    private String ruleValue;

    private String remark;
}
