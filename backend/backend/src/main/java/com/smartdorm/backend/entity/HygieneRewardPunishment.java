package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_hygiene_reward_punishment")
@EqualsAndHashCode(callSuper = true)
public class HygieneRewardPunishment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    private String rpType;

    private Integer points;

    private String reason;

    private Long relatedScoreId;

    private Long createdBy;
}
