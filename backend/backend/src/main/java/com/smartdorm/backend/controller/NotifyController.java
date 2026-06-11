package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.service.NotifyInboxService;
import com.smartdorm.backend.vo.NotifyInboxVO;
import com.smartdorm.backend.vo.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
@CrossOrigin
public class NotifyController {

    private final NotifyInboxService notifyInboxService;

    @GetMapping("/inbox")
    public Result<List<NotifyInboxVO>> list(@RequestParam Long userId,
                                           @RequestParam(required = false) Boolean unreadOnly,
                                           @RequestParam(required = false) String bizType,
                                           @RequestParam(required = false) Integer limit) {
        return Result.success(notifyInboxService.listInbox(userId, unreadOnly, bizType, limit));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount(@RequestParam Long userId) {
        return Result.success(notifyInboxService.unreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id, @RequestParam Long userId) {
        notifyInboxService.markRead(id, userId);
        return Result.success();
    }
}

