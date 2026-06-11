package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_adjust_request")
@EqualsAndHashCode(callSuper = true)
public class AdjustRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long studentId;

    private Long currentRoomId;

    private Long targetRoomId;

    private String reason;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime handleTime;
}
