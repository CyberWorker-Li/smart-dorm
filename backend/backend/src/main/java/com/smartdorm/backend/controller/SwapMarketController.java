package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.SendSwapChatMessageRequest;
import com.smartdorm.backend.dto.StartSwapChatRequest;
import com.smartdorm.backend.dto.UpsertSwapIntentRequest;
import com.smartdorm.backend.service.SwapMarketService;
import com.smartdorm.backend.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/swap-market")
@RequiredArgsConstructor
@CrossOrigin
public class SwapMarketController {

    private final SwapMarketService swapMarketService;

    @GetMapping("/rooms")
    public Result<List<SwapMarketRoomVO>> listRooms(@RequestParam(required = false) Long batchId,
                                                   @RequestParam(required = false) String gender) {
        return Result.success(swapMarketService.listMarketRooms(batchId, gender));
    }

    @GetMapping("/my-intent")
    public Result<SwapMarketMyIntentVO> getMyIntent(@RequestParam(required = false) Long batchId,
                                                    @RequestParam Long studentId) {
        return Result.success(swapMarketService.getMyIntent(batchId, studentId));
    }

    @PostMapping("/intent")
    public Result<SwapMarketMyIntentVO> upsertIntent(@Valid @RequestBody UpsertSwapIntentRequest body) {
        return Result.success(swapMarketService.upsertIntent(body.getBatchId(), body.getStudentId(), body.getRemark()));
    }

    @PutMapping("/intent/close")
    public Result<SwapMarketMyIntentVO> closeIntent(@RequestParam(required = false) Long batchId,
                                                    @RequestParam Long studentId) {
        return Result.success(swapMarketService.closeIntent(batchId, studentId));
    }

    @PostMapping("/chat/start")
    public Result<Long> startChat(@Valid @RequestBody StartSwapChatRequest body) {
        return Result.success(swapMarketService.startChat(
                body.getBatchId(),
                body.getStudentId(),
                body.getTargetStudentId(),
                body.getTargetRoomId()));
    }

    @GetMapping("/chat/threads")
    public Result<List<SwapChatThreadVO>> listThreads(@RequestParam Long studentId) {
        return Result.success(swapMarketService.listThreads(studentId));
    }

    @GetMapping("/chat/messages")
    public Result<List<SwapChatMessageVO>> listMessages(@RequestParam Long threadId,
                                                        @RequestParam Long studentId) {
        return Result.success(swapMarketService.listMessages(threadId, studentId));
    }

    @PostMapping("/chat/message")
    public Result<SwapChatMessageVO> sendMessage(@Valid @RequestBody SendSwapChatMessageRequest body) {
        return Result.success(swapMarketService.sendMessage(body.getThreadId(), body.getStudentId(), body.getContent()));
    }
}

