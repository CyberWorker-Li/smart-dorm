package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.HandleAdjustRequest;
import com.smartdorm.backend.dto.SubmitAdjustRequest;
import com.smartdorm.backend.service.AdjustRequestService;
import com.smartdorm.backend.vo.AdjustRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/adjust-request")
@RequiredArgsConstructor
@CrossOrigin
public class AdjustRequestController {

    private final AdjustRequestService adjustRequestService;

    /** 学生：提交微调申请 */
    @PostMapping
    public Result<AdjustRequestVO> submit(@Valid @RequestBody SubmitAdjustRequest request) {
        return Result.success(adjustRequestService.submitRequest(request));
    }

    /** 学生：查询自己的申请列表 */
    @GetMapping("/my")
    public Result<List<AdjustRequestVO>> listMy(@RequestParam Long studentId) {
        return Result.success(adjustRequestService.listMyRequests(studentId));
    }

    /** 宿管：查询待处理申请 */
    @GetMapping("/pending")
    public Result<List<AdjustRequestVO>> listPending(@RequestParam Long managerId) {
        return Result.success(adjustRequestService.listPendingRequests(managerId));
    }

    /** 宿管：处理申请（通过/拒绝） */
    @PutMapping("/handle")
    public Result<AdjustRequestVO> handle(@Valid @RequestBody HandleAdjustRequest request) {
        return Result.success(adjustRequestService.handleRequest(request));
    }
}
