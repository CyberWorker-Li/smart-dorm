package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssignmentBatchVO {

    private Long id;
    private Long questionnaireId;
    private String questionnaireTitle;
    private String academicYear;
    private String status;
    private String remark;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private Integer totalAssigned;
}
