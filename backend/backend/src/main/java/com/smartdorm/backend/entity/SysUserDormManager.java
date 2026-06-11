package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_dorm_manager")
public class SysUserDormManager {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
}
