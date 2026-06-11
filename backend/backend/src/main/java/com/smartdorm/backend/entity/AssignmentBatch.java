package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_assignment_batch")
@EqualsAndHashCode(callSuper = true)
public class AssignmentBatch extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionnaireId;

    private String academicYear;

    /** DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    private String remark;

    private Long createBy;

    private LocalDateTime publishTime;
}
