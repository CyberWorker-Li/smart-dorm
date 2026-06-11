package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.GenderUtil;
import com.smartdorm.backend.dto.HandleAdjustRequest;
import com.smartdorm.backend.dto.SubmitAdjustRequest;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.AdjustRequestVO;
import com.smartdorm.backend.vo.DormRoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdjustRequestService {

    private final AdjustRequestMapper adjustRequestMapper;
    private final AssignmentResultMapper resultMapper;
    private final AssignmentBatchMapper batchMapper;
    private final DormRoomMapper dormRoomMapper;
    private final DormRoomService dormRoomService;
    private final UserMapper userMapper;

    @Transactional
    public AdjustRequestVO submitRequest(SubmitAdjustRequest request) {
        AssignmentBatch batch = batchMapper.selectById(request.getBatchId());
        if (batch == null || !"PUBLISHED".equals(batch.getStatus())) {
            throw new BusinessException("分配结果尚未公示，无法提交申请");
        }

        AssignmentResult myResult = resultMapper.selectOne(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, request.getBatchId())
                        .eq(AssignmentResult::getStudentId, request.getStudentId()));
        if (myResult == null) throw new BusinessException("未找到您的分配记录");

        // 同一批次同一学生只允许一条待处理申请
        Long pending = adjustRequestMapper.selectCount(
                new LambdaQueryWrapper<AdjustRequest>()
                        .eq(AdjustRequest::getBatchId, request.getBatchId())
                        .eq(AdjustRequest::getStudentId, request.getStudentId())
                        .eq(AdjustRequest::getStatus, "PENDING"));
        if (pending > 0) throw new BusinessException("您已有一条待处理的申请，请等待宿管处理后再提交");

        if (request.getTargetRoomId() != null) {
            DormRoom targetRoom = dormRoomMapper.selectById(request.getTargetRoomId());
            if (targetRoom == null) throw new BusinessException("目标房间不存在");
            User student = userMapper.selectById(request.getStudentId());
            String bg = dormRoomService.resolveBuildingGender(request.getTargetRoomId());
            GenderUtil.requireUserMatchesBuildingGender(student, bg, false);
        }

        AdjustRequest adj = new AdjustRequest();
        adj.setBatchId(request.getBatchId());
        adj.setStudentId(request.getStudentId());
        adj.setCurrentRoomId(myResult.getRoomId());
        adj.setTargetRoomId(request.getTargetRoomId());
        adj.setReason(request.getReason());
        adj.setStatus("PENDING");
        adjustRequestMapper.insert(adj);
        return toVO(adj);
    }

    public List<AdjustRequestVO> listMyRequests(Long studentId) {
        return adjustRequestMapper.selectList(
                new LambdaQueryWrapper<AdjustRequest>()
                        .eq(AdjustRequest::getStudentId, studentId)
                        .orderByDesc(AdjustRequest::getId))
                .stream().map(this::toVO).toList();
    }

    public List<AdjustRequestVO> listPendingRequests(Long managerId) {
        // 宿管只看自己负责房间的申请
        List<DormRoomVO> myRoomVos = dormRoomService.listRoomsByManager(managerId);
        if (myRoomVos.isEmpty()) return List.of();

        List<Long> roomIds = myRoomVos.stream().map(DormRoomVO::getId).toList();
        return adjustRequestMapper.selectList(
                new LambdaQueryWrapper<AdjustRequest>()
                        .in(AdjustRequest::getCurrentRoomId, roomIds)
                        .eq(AdjustRequest::getStatus, "PENDING")
                        .orderByAsc(AdjustRequest::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public AdjustRequestVO handleRequest(HandleAdjustRequest request) {
        AdjustRequest adj = adjustRequestMapper.selectById(request.getRequestId());
        if (adj == null) throw new BusinessException("申请不存在");
        if (!"PENDING".equals(adj.getStatus())) throw new BusinessException("该申请已处理");

        if (!"APPROVED".equals(request.getStatus()) && !"REJECTED".equals(request.getStatus())) {
            throw new BusinessException("处理结果只能为 APPROVED 或 REJECTED");
        }

        adj.setStatus(request.getStatus());
        adj.setHandlerId(request.getHandlerId());
        adj.setHandleRemark(request.getHandleRemark());
        adj.setHandleTime(LocalDateTime.now());
        adjustRequestMapper.updateById(adj);

        // 审批通过时更新分配结果
        if ("APPROVED".equals(request.getStatus()) && adj.getTargetRoomId() != null) {
            DormRoom targetRoom = dormRoomMapper.selectById(adj.getTargetRoomId());
            if (targetRoom == null) throw new BusinessException("目标房间不存在");

            User student = userMapper.selectById(adj.getStudentId());
            String bg = dormRoomService.resolveBuildingGender(adj.getTargetRoomId());
            GenderUtil.requireUserMatchesBuildingGender(student, bg, false);

            Long occupied = resultMapper.selectCount(
                    new LambdaQueryWrapper<AssignmentResult>()
                            .eq(AssignmentResult::getBatchId, adj.getBatchId())
                            .eq(AssignmentResult::getRoomId, adj.getTargetRoomId()));
            if (occupied >= targetRoom.getCapacity()) throw new BusinessException("目标房间已满，无法调入");

            AssignmentResult result = resultMapper.selectOne(
                    new LambdaQueryWrapper<AssignmentResult>()
                            .eq(AssignmentResult::getBatchId, adj.getBatchId())
                            .eq(AssignmentResult::getStudentId, adj.getStudentId()));
            if (result != null) {
                result.setRoomId(adj.getTargetRoomId());
                result.setBedNo(occupied.intValue() + 1);
                resultMapper.updateById(result);
            }
        }

        return toVO(adj);
    }

    private AdjustRequestVO toVO(AdjustRequest adj) {
        AdjustRequestVO vo = new AdjustRequestVO();
        vo.setId(adj.getId());
        vo.setBatchId(adj.getBatchId());
        vo.setStudentId(adj.getStudentId());
        vo.setReason(adj.getReason());
        vo.setStatus(adj.getStatus());
        vo.setHandleRemark(adj.getHandleRemark());
        vo.setHandleTime(adj.getHandleTime());
        vo.setCreateTime(adj.getCreateTime());

        User student = userMapper.selectById(adj.getStudentId());
        if (student != null) {
            vo.setStudentName(student.getRealName());
            vo.setStudentNo(student.getUserNo());
        }

        DormRoom currentRoom = dormRoomMapper.selectById(adj.getCurrentRoomId());
        if (currentRoom != null) {
            vo.setCurrentBuilding(currentRoom.getBuilding());
            vo.setCurrentRoomNo(dormRoomService.formatRoomDisplay(currentRoom));
        }

        if (adj.getTargetRoomId() != null) {
            DormRoom targetRoom = dormRoomMapper.selectById(adj.getTargetRoomId());
            if (targetRoom != null) {
                vo.setTargetBuilding(targetRoom.getBuilding());
                vo.setTargetRoomNo(dormRoomService.formatRoomDisplay(targetRoom));
            }
        }
        return vo;
    }
}
