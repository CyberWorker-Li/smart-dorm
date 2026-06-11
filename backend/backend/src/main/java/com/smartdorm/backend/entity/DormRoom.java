package com.smartdorm.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("dorm_room")
@EqualsAndHashCode(callSuper = true)
public class DormRoom extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long floorId;

    /** 楼栋显示名，可独立于楼房名称修改 */
    private String building;

    /** 本层房间序号，从 1 开始 */
    private Integer roomNo;

    private Integer capacity;

    private Boolean valid;

    /** 查询分配用：非表字段 */
    @TableField(exist = false)
    private String buildingGender;
}
