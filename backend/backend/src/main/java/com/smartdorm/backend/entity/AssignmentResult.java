package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_assignment_result")
@EqualsAndHashCode(callSuper = true)
public class AssignmentResult extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long studentId;

    private Long roomId;

    private Integer bedNo;
}
