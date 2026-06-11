package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_rule_proposal")
@EqualsAndHashCode(callSuper = true)
public class DormRuleProposal extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long roomId;

    private Long proposerStudentId;

    private String content;

    private String status;
}

