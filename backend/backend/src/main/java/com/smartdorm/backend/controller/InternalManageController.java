package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.CreateDormFeedbackRequest;
import com.smartdorm.backend.dto.CreateDormRuleRequest;
import com.smartdorm.backend.dto.CreateRepairRequest;
import com.smartdorm.backend.dto.SaveUtilityThresholdRequest;
import com.smartdorm.backend.dto.SubmitPeerEvalRequest;
import com.smartdorm.backend.dto.VoteDormRuleRequest;
import com.smartdorm.backend.service.InternalManageService;
import com.smartdorm.backend.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@CrossOrigin
public class InternalManageController {

    private final InternalManageService internalManageService;

    @GetMapping("/members")
    public Result<List<RoommateVO>> members(@RequestParam Long studentId) {
        return Result.success(internalManageService.listMembers(studentId));
    }

    @GetMapping("/rules")
    public Result<List<DormRuleVO>> listRules(@RequestParam Long studentId) {
        return Result.success(internalManageService.listRules(studentId));
    }

    @PostMapping("/rules")
    public Result<Long> createRule(@Valid @RequestBody CreateDormRuleRequest request) {
        return Result.success(internalManageService.createRule(request));
    }

    @PostMapping("/rules/{id}/vote")
    public Result<Boolean> voteRule(@PathVariable Long id, @Valid @RequestBody VoteDormRuleRequest request) {
        return Result.success(internalManageService.voteRule(id, request));
    }

    @GetMapping("/feedback")
    public Result<List<DormFeedbackVO>> listFeedback(@RequestParam Long studentId) {
        return Result.success(internalManageService.listFeedback(studentId));
    }

    @PostMapping("/feedback")
    public Result<Long> createFeedback(@Valid @RequestBody CreateDormFeedbackRequest request) {
        return Result.success(internalManageService.createFeedback(request));
    }

    @PutMapping("/feedback/{id}/escalate")
    public Result<Boolean> escalate(@PathVariable Long id, @RequestParam Long studentId) {
        return Result.success(internalManageService.escalateFeedback(id, studentId));
    }

    @GetMapping("/peer-eval/template")
    public Result<List<PeerEvalTemplateItemVO>> peerEvalTemplate(@RequestParam Long studentId) {
        return Result.success(internalManageService.peerEvalTemplate(studentId));
    }

    @PostMapping("/peer-eval/submit")
    public Result<Boolean> submitPeerEval(@Valid @RequestBody SubmitPeerEvalRequest request) {
        return Result.success(internalManageService.submitPeerEval(request));
    }

    @GetMapping("/peer-eval/manager/list")
    public Result<List<PeerEvalManagerListItemVO>> peerEvalListForManager(@RequestParam Long managerId,
                                                                          @RequestParam(required = false) String month,
                                                                          @RequestParam(required = false) Long roomId,
                                                                          @RequestParam(required = false) Integer limit) {
        return Result.success(internalManageService.listPeerEvalForManager(managerId, month, roomId, limit));
    }

    @GetMapping("/peer-eval/manager/detail")
    public Result<PeerEvalManagerDetailVO> peerEvalDetailForManager(@RequestParam Long managerId,
                                                                    @RequestParam Long evalId) {
        return Result.success(internalManageService.peerEvalDetailForManager(managerId, evalId));
    }

    @PostMapping("/repairs")
    public Result<RepairSubmitVO> submitRepair(@Valid @RequestBody CreateRepairRequest request) {
        return Result.success(internalManageService.submitRepair(request));
    }

    @GetMapping("/utilities")
    public Result<List<UtilityVO>> listUtilities(@RequestParam Long studentId) {
        return Result.success(internalManageService.listUtilities(studentId));
    }

    @GetMapping("/utilities/threshold")
    public Result<UtilityThresholdVO> getThreshold(@RequestParam Long studentId) {
        return Result.success(internalManageService.getUtilityThreshold(studentId));
    }

    @PostMapping("/utilities/threshold")
    public Result<Boolean> saveThreshold(@Valid @RequestBody SaveUtilityThresholdRequest request) {
        return Result.success(internalManageService.saveUtilityThreshold(request));
    }

    @GetMapping("/activities")
    public Result<List<ActivityVO>> listActivities(@RequestParam Long studentId) {
        return Result.success(internalManageService.listActivities(studentId));
    }

    @PostMapping("/activities/{id}/signup")
    public Result<Boolean> signup(@PathVariable Long id, @RequestParam Long studentId) {
        return Result.success(internalManageService.signupActivity(id, studentId));
    }
}
