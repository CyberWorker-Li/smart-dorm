package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("notice")
@EqualsAndHashCode(callSuper = true)
public class Notice extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long publisherId;

    private String title;

    private String content;

    private String scopeType;

    private Long buildingId;

    private Long floorId;

    private String status;

    private LocalDateTime publishTime;
}

