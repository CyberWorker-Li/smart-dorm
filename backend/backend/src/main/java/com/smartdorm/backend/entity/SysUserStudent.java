package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_student")
public class SysUserStudent {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    @TableField("is_leader")
    private Boolean leader;

    @TableField("academic_year")
    private String academicYear;
}
