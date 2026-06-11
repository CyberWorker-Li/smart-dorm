package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.AutoAssignRequest;
import com.smartdorm.backend.dto.BulkBuildingMoveRequest;
import com.smartdorm.backend.dto.CreateBatchRequest;
import com.smartdorm.backend.dto.ManualAssignRequest;
import com.smartdorm.backend.service.AssignmentService;
import com.smartdorm.backend.entity.AssignmentConflictWarning;
import com.smartdorm.backend.vo.AssignmentBatchVO;
import com.smartdorm.backend.vo.MyAssignmentResultVO;
import com.smartdorm.backend.vo.RoommateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignment")
@RequiredArgsConstructor
@CrossOrigin
public class AssignmentController {

    private final AssignmentService assignmentService;

    /** 管理员：查询所有批次 */
    @GetMapping("/batches")
    public Result<List<AssignmentBatchVO>> listBatches() {
        return Result.success(assignmentService.listBatches());
    }

    /** 管理员：创建分配批次 */
    @PostMapping("/batches")
    public Result<AssignmentBatchVO> createBatch(@Valid @RequestBody CreateBatchRequest request,
                                                 @RequestParam Long adminId) {
        return Result.success(assignmentService.createBatch(request, adminId));
    }

    /**
     * 管理员：触发自动分配。请求体可选：reassignScope（FULL / SAME_ACADEMIC_YEAR / INCREMENTAL）、
     * targetBuildingId（仅在某楼内分）、restrictStudentIds（仅给这些学生分，内部换楼用）。
     */
    @PostMapping("/batches/{batchId}/auto-assign")
    public Result<Void> autoAssign(@PathVariable Long batchId,
                                   @RequestBody(required = false) AutoAssignRequest body) {
        assignmentService.autoAssign(batchId, body);
        return Result.success();
    }

    /** 管理员：公示分配结果 */
    @PutMapping("/batches/{batchId}/publish")
    public Result<Void> publishBatch(@PathVariable Long batchId) {
        assignmentService.publishBatch(batchId);
        return Result.success();
    }

    /** 管理员：手动将学生编入某房间（仅草稿批次） */
    @PutMapping("/manual")
    public Result<Void> manualAssign(@Valid @RequestBody ManualAssignRequest request) {
        assignmentService.manualAssign(request);
        return Result.success();
    }

    /** 管理员：将学生从本批次分配结果中移出（仅草稿批次） */
    @DeleteMapping("/batches/{batchId}/students/{studentId}")
    public Result<Void> removeStudentFromBatch(@PathVariable Long batchId,
                                              @PathVariable Long studentId) {
        assignmentService.removeStudentFromBatch(batchId, studentId);
        return Result.success();
    }

    /** 管理员：按学年+性别将学生从源楼迁出并在目标楼自动分配（仅草稿批次） */
    @PostMapping("/bulk-building-move")
    public Result<Void> bulkBuildingMove(@Valid @RequestBody BulkBuildingMoveRequest request) {
        assignmentService.bulkRelocateBetweenBuildings(request);
        return Result.success();
    }

    /** 学生：查询自己的分配结果（含脱敏室友信息） */
    @GetMapping("/my-result")
    public Result<MyAssignmentResultVO> getMyResult(@RequestParam Long studentId) {
        return Result.success(assignmentService.getMyResult(studentId));
    }

    /** 宿管：查询某房间的分配结果 */
    @GetMapping("/room-result")
    public Result<List<RoommateVO>> getRoomResult(@RequestParam Long batchId,
                                                  @RequestParam Long roomId) {
        return Result.success(assignmentService.getRoomResult(batchId, roomId));
    }

    /** 管理员：查看某批次冲突预警（精准分配算法生成） */
    @GetMapping("/batches/{batchId}/conflicts")
    public Result<List<AssignmentConflictWarning>> conflicts(@PathVariable Long batchId) {
        return Result.success(assignmentService.listConflictWarnings(batchId));
    }

    @GetMapping("/batches/{batchId}/acceptance")
    public Result<AssignmentService.AcceptanceReport> acceptance(@PathVariable Long batchId,
                                                                 @RequestParam(defaultValue = "false") boolean strict) {
        return Result.success(assignmentService.acceptanceReport(batchId, strict));
    }
}
