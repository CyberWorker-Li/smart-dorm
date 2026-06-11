package com.smartdorm.backend.vo;

import lombok.Data;

import java.util.List;

@Data
public class MyAssignmentResultVO {

    private Long batchId;
    private String academicYear;
    private String building;
    private String roomNo;
    /** 当前分配房间性别类型 MALE/FEMALE */
    private String roomGender;
    private Integer bedNo;
    private List<RoommateVO> roommates;
}
