package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_feedback")
@EqualsAndHashCode(callSuper = true)
public class DormFeedback extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long roomId;

    private Long submitterStudentId;

    private String content;

    private String status;

    private LocalDateTime escalateTime;

    private LocalDateTime resolveTime;
}

