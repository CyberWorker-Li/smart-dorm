package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_swap_request")
@EqualsAndHashCode(callSuper = true)
public class SwapRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long studentAId;

    private Long studentARoomId;

    private Long studentBId;

    private Long studentBRoomId;

    private String initiatorRemark;

    /** PENDING_B_CONFIRM / PENDING_MANAGER / APPROVED / REJECTED_BY_B / REJECTED_BY_MANAGER / CANCELLED */
    private String status;

    /** null=未操作, CONFIRMED, REJECTED */
    private String bConfirmStatus;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime handleTime;
}
