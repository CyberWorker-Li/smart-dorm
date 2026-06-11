package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@TableName("dorm_hygiene_score")
@EqualsAndHashCode(callSuper = true)
public class HygieneScore extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private LocalDate scoreDate;

    private String periodType;

    private Integer score;

    private String sourceType;

    private String reason;

    private Long inspectorId;
}
