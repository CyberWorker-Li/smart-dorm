package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_repair_request")
@EqualsAndHashCode(callSuper = true)
public class DormRepairRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long roomId;

    private Long submitterStudentId;

    private String type;

    private String detail;

    private String reply;

    private String status;
}

