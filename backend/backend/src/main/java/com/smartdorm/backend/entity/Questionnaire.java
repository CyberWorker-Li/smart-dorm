package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("dorm_questionnaire")
@EqualsAndHashCode(callSuper = true)
public class Questionnaire extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String academicYear;

    /** DRAFT / PUBLISHED / CLOSED */
    private String status;

    private LocalDateTime deadline;

    private Long createBy;

    /** 逻辑删除：管理员删除问卷模板后仍为 false 的才可展示；答卷不删 */
    private Boolean deleted;
}
