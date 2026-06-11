package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.CreateNoticeRequest;
import com.smartdorm.backend.service.NoticeService;
import com.smartdorm.backend.vo.NoticeStatsVO;
import com.smartdorm.backend.vo.NoticeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
@CrossOrigin
public class NoticeController {

    private final NoticeService noticeService;

    @PostMapping
    public Result<NoticeVO> createDraft(@Valid @RequestBody CreateNoticeRequest request) {
        return Result.success(noticeService.createDraft(
                request.getPublisherId(),
                request.getTitle(),
                request.getContent(),
                request.getScopeType(),
                request.getBuildingId(),
                request.getFloorId()
        ));
    }

    @GetMapping("/{id}")
    public Result<NoticeVO> detail(@PathVariable Long id) {
        return Result.success(noticeService.getDetail(id));
    }

    @GetMapping("/for-manager")
    public Result<List<NoticeVO>> listForManager(@RequestParam Long managerId,
                                                 @RequestParam(required = false) String status) {
        return Result.success(noticeService.listForManager(managerId, status));
    }

    @PutMapping("/{id}/publish")
    public Result<NoticeVO> publish(@PathVariable Long id, @RequestParam Long managerId) {
        return Result.success(noticeService.publish(id, managerId));
    }

    @GetMapping("/{id}/stats")
    public Result<NoticeStatsVO> stats(@PathVariable Long id, @RequestParam Long managerId) {
        return Result.success(noticeService.stats(id, managerId));
    }
}

