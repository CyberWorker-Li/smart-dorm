package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_building")
@EqualsAndHashCode(callSuper = true)
public class DormBuilding extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer floorCount;

    private String gender;

    private Boolean valid;
}
