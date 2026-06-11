package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.*;
import com.smartdorm.backend.service.HygieneService;
import com.smartdorm.backend.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/hygiene")
@RequiredArgsConstructor
@CrossOrigin
public class HygieneController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HygieneService hygieneService;

    @GetMapping("/my-tasks")
    public Result<List<HygieneTaskVO>> myTasks(@RequestParam Long studentId,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return Result.success(hygieneService.listMyTasks(studentId, parse(from), parse(to)));
    }

    @GetMapping("/room-tasks")
    public Result<List<HygieneTaskVO>> roomTasks(@RequestParam Long studentId,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return Result.success(hygieneService.listRoomTasks(studentId, parse(from), parse(to)));
    }

    @PostMapping("/leader/generate-week")
    public Result<Integer> generateWeek(@RequestBody HygieneGenerateWeekRequest request) {
        return Result.success(hygieneService.generateWeek(request));
    }

    @PutMapping("/leader/reassign")
    public Result<Void> reassign(@Valid @RequestBody HygieneReassignRequest request) {
        hygieneService.reassign(request);
        return Result.success();
    }

    @GetMapping("/leader/members")
    public Result<List<HygieneMemberVO>> leaderMembers(@RequestParam Long leaderId) {
        return Result.success(hygieneService.leaderMembers(leaderId));
    }

    @PostMapping("/leader/upsert-task")
    public Result<Long> upsertTask(@Valid @RequestBody HygieneLeaderUpsertTaskRequest request) {
        return Result.success(hygieneService.upsertTask(request));
    }

    @PostMapping("/leader/delete-week")
    public Result<Integer> deleteWeek(@Valid @RequestBody HygieneLeaderDeleteWeekRequest request) {
        return Result.success(hygieneService.deleteWeek(request));
    }

    @PostMapping("/checkin")
    public Result<Long> checkin(@Valid @RequestBody HygieneCheckinRequest request) {
        return Result.success(hygieneService.checkin(request));
    }

    @GetMapping("/room-summary")
    public Result<HygieneRoomSummaryVO> myRoomSummary(@RequestParam Long studentId) {
        return Result.success(hygieneService.myRoomSummary(studentId));
    }

    @GetMapping("/manager/room-tasks")
    public Result<List<HygieneTaskVO>> managerRoomTasks(@RequestParam Long managerId,
                                                        @RequestParam Long roomId,
                                                        @RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to) {
        return Result.success(hygieneService.managerRoomTasks(managerId, roomId, parse(from), parse(to)));
    }

    @GetMapping("/manager/pending-checkins")
    public Result<List<HygieneTaskVO>> managerPending(@RequestParam Long managerId,
                                                      @RequestParam Long roomId) {
        return Result.success(hygieneService.managerPendingCheckins(managerId, roomId));
    }

    @PutMapping("/manager/verify-checkin")
    public Result<Void> verify(@Valid @RequestBody HygieneVerifyCheckinRequest request) {
        hygieneService.verifyCheckin(request);
        return Result.success();
    }

    @PostMapping("/manager/inspect")
    public Result<Long> inspect(@Valid @RequestBody HygieneInspectRequest request) {
        return Result.success(hygieneService.inspect(request));
    }

    @GetMapping("/admin/rules")
    public Result<List<HygieneRuleVO>> rules() {
        return Result.success(hygieneService.listRules());
    }

    @PutMapping("/admin/rules")
    public Result<Void> upsertRules(@Valid @RequestBody HygieneUpsertRulesRequest request) {
        hygieneService.upsertRules(request);
        return Result.success();
    }

    @GetMapping("/admin/ranking")
    public Result<List<HygieneRankingItemVO>> ranking(@RequestParam(required = false) String month) {
        return Result.success(hygieneService.monthRanking(month));
    }

    @GetMapping(value = "/admin/ranking/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportRanking(@RequestParam(required = false) String month) {
        return hygieneService.exportMonthRankingCsv(month);
    }

    @GetMapping("/admin/weekly")
    public Result<List<HygieneRankingItemVO>> weekly(@RequestParam(required = false) String weekStart) {
        return Result.success(hygieneService.weekRanking(weekStart));
    }

    @GetMapping(value = "/admin/weekly/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportWeekly(@RequestParam(required = false) String weekStart) {
        return hygieneService.exportWeekRankingCsv(weekStart);
    }

    private static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw.trim(), DATE);
    }
}
