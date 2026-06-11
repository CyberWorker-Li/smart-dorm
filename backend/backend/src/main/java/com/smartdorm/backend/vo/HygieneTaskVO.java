package com.smartdorm.backend.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class HygieneTaskVO {
    private Long id;
    private Long roomId;
    private String roomDisplay;
    private LocalDate dutyDate;
    private Long dutyUserId;
    private String dutyUserName;
    private String dutyItem;
    private String status;
    private LocalDateTime deadlineTime;
    private boolean checkedIn;
    private Long checkinId;
    private String checkinPhotoUrl;
    private LocalDateTime checkinTime;
    private String verifyStatus;
    private String checkinLocationText;
    private String checkinRemark;
}

