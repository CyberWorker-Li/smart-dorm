package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_conflict_warning")
@EqualsAndHashCode(callSuper = true)
public class AssignmentConflictWarning extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long roomId;

    private Long studentId1;

    private Long studentId2;

    private Double conflictScore;

    private String description;

    private String status;   // PENDING / RESOLVED
}