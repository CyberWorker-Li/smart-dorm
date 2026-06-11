package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_floor")
@EqualsAndHashCode(callSuper = true)
public class DormFloor extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long buildingId;

    private Integer seqNo;

    private Integer floorNo;

    private Integer maxRooms;

    private Boolean valid;
}
