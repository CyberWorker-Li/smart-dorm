package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.HandleSwapRequest;
import com.smartdorm.backend.dto.SubmitSwapRequest;
import com.smartdorm.backend.service.SwapRequestService;
import com.smartdorm.backend.vo.SwapRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/swap-request")
@RequiredArgsConstructor
@CrossOrigin
public class SwapRequestController {

    private final SwapRequestService swapRequestService;

    /** 学生A：发起互换申请 */
    @PostMapping
    public Result<SwapRequestVO> submit(@Valid @RequestBody SubmitSwapRequest request) {
        return Result.success(swapRequestService.submitSwapRequest(request));
    }

    /** 学生：查看自己的互换申请列表（作为A或B） */
    @GetMapping("/my")
    public Result<List<SwapRequestVO>> listMy(@RequestParam Long studentId) {
        return Result.success(swapRequestService.listMyRequests(studentId));
    }

    /** 学生B：查看待自己确认的互换申请 */
    @GetMapping("/incoming")
    public Result<List<SwapRequestVO>> listIncoming(@RequestParam Long studentId) {
        return Result.success(swapRequestService.listIncomingForB(studentId));
    }

    /** 学生B：确认互换 */
    @PutMapping("/{id}/confirm")
    public Result<SwapRequestVO> confirm(@PathVariable Long id, @RequestParam Long studentBId) {
        return Result.success(swapRequestService.confirmSwapRequest(id, studentBId));
    }

    /** 学生B：拒绝互换 */
    @PutMapping("/{id}/reject")
    public Result<SwapRequestVO> reject(@PathVariable Long id, @RequestParam Long studentBId) {
        return Result.success(swapRequestService.rejectSwapRequest(id, studentBId));
    }

    /** 学生A：取消申请 */
    @PutMapping("/{id}/cancel")
    public Result<SwapRequestVO> cancel(@PathVariable Long id, @RequestParam Long studentAId) {
        return Result.success(swapRequestService.cancelSwapRequest(id, studentAId));
    }

    /** 宿管：查看待处理的互换申请（双方均已确认） */
    @GetMapping("/pending")
    public Result<List<SwapRequestVO>> listPending(@RequestParam Long managerId) {
        return Result.success(swapRequestService.listPendingForManager(managerId));
    }

    /** 宿管：处理互换申请（通过/拒绝） */
    @PutMapping("/handle")
    public Result<SwapRequestVO> handle(@Valid @RequestBody HandleSwapRequest request) {
        return Result.success(swapRequestService.handleSwapRequest(request));
    }
}
