package com.smartdorm.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HandleSwapRequest {

    @NotNull(message = "互换申请ID不能为空")
    private Long requestId;

    @NotNull(message = "处理人ID不能为空")
    private Long handlerId;

    /** APPROVED / REJECTED_BY_MANAGER */
    @NotBlank(message = "处理结果不能为空")
    private String status;

    private String handleRemark;
}
