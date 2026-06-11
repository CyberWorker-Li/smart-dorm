package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.CreateNoiseRequest;
import com.smartdorm.backend.service.NoiseRequestService;
import com.smartdorm.backend.vo.NoiseRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/noise-request")
@RequiredArgsConstructor
@CrossOrigin
public class NoiseRequestController {

    private final NoiseRequestService noiseRequestService;

    @PostMapping
    public Result<NoiseRequestVO> create(@Valid @RequestBody CreateNoiseRequest request) {
        return Result.success(noiseRequestService.create(
                request.getBatchId(),
                request.getFromStudentId(),
                request.getToRoomId(),
                request.getContent()));
    }

    @GetMapping("/my")
    public Result<List<NoiseRequestVO>> listMy(@RequestParam Long studentId) {
        return Result.success(noiseRequestService.listMy(studentId));
    }

    @GetMapping("/incoming")
    public Result<List<NoiseRequestVO>> listIncoming(@RequestParam Long studentId) {
        return Result.success(noiseRequestService.listIncoming(studentId));
    }

    @GetMapping("/for-manager")
    public Result<List<NoiseRequestVO>> listForManager(@RequestParam Long managerId) {
        return Result.success(noiseRequestService.listForManager(managerId));
    }

    @PutMapping("/{id}/ack")
    public Result<NoiseRequestVO> ack(@PathVariable Long id, @RequestParam Long studentId) {
        return Result.success(noiseRequestService.ack(id, studentId));
    }

    @PutMapping("/{id}/escalate")
    public Result<NoiseRequestVO> escalate(@PathVariable Long id, @RequestParam Long studentId) {
        return Result.success(noiseRequestService.escalate(id, studentId));
    }

    @PutMapping("/{id}/resolve")
    public Result<NoiseRequestVO> resolve(@PathVariable Long id,
                                          @RequestParam Long managerId,
                                          @RequestParam(required = false) String remark) {
        return Result.success(noiseRequestService.resolve(id, managerId, remark));
    }

    @PutMapping("/{id}/cancel")
    public Result<NoiseRequestVO> cancel(@PathVariable Long id, @RequestParam Long studentId) {
        return Result.success(noiseRequestService.cancel(id, studentId));
    }
}

