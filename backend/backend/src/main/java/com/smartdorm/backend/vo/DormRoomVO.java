package com.smartdorm.backend.vo;

import lombok.Data;

@Data
public class DormRoomVO {

    private Long id;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private Integer floorNo;
    /** 本层房间序号 */
    private Integer roomNo;
    /** 房间表上的楼栋显示字段，可修改 */
    private String building;
    private Integer capacity;
    /** 来自楼房 */
    private String gender;
    private Boolean valid;
    private Boolean floorValid;
    private Boolean buildingValid;
    /** 当前已分配人数（最新已发布批次） */
    private Integer occupiedCount;
}
