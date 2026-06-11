package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_rule_vote")
@EqualsAndHashCode(callSuper = true)
public class DormRuleVote extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long proposalId;

    private Long voterStudentId;

    private Boolean agree;
}

