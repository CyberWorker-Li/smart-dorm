package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("noise_request")
@EqualsAndHashCode(callSuper = true)
public class NoiseRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long fromRoomId;

    private Long toRoomId;

    private String content;

    private String status;

    private LocalDateTime ackTime;

    private LocalDateTime escalateTime;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime handleTime;
}

