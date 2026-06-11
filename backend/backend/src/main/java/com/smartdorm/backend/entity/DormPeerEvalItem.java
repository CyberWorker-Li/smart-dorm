package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_peer_eval_item")
@EqualsAndHashCode(callSuper = true)
public class DormPeerEvalItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evalId;

    private Long targetStudentId;

    private Integer scheduleScore;

    private Integer hygieneScore;

    private Integer communicationScore;
}

