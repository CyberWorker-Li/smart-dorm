package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.GenderUtil;
import com.smartdorm.backend.dto.HandleSwapRequest;
import com.smartdorm.backend.dto.SubmitSwapRequest;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.DormRoomVO;
import com.smartdorm.backend.vo.SwapRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SwapRequestService {

    private final SwapRequestMapper swapRequestMapper;
    private final AssignmentResultMapper resultMapper;
    private final AssignmentBatchMapper batchMapper;
    private final DormRoomMapper dormRoomMapper;
    private final DormRoomService dormRoomService;
    private final UserMapper userMapper;

    @Transactional
    public SwapRequestVO submitSwapRequest(SubmitSwapRequest request) {
        if (request.getStudentAId().equals(request.getStudentBId())) {
            throw new BusinessException("不能和自己互换");
        }

        AssignmentBatch batch = batchMapper.selectById(request.getBatchId());
        if (batch == null || !"PUBLISHED".equals(batch.getStatus())) {
            throw new BusinessException("分配结果尚未公示，无法提交互换申请");
        }

        AssignmentResult resultA = resultMapper.selectOne(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, request.getBatchId())
                        .eq(AssignmentResult::getStudentId, request.getStudentAId()));
        if (resultA == null) throw new BusinessException("未找到您的分配记录");

        AssignmentResult resultB = resultMapper.selectOne(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, request.getBatchId())
                        .eq(AssignmentResult::getStudentId, request.getStudentBId()));
        if (resultB == null) throw new BusinessException("目标学生暂无分配记录");

        if (resultA.getRoomId().equals(resultB.getRoomId())) {
            throw new BusinessException("双方已在同一房间，无需互换");
        }

        // 检查A是否已有进行中的互换申请
        Long aPending = swapRequestMapper.selectCount(
                new LambdaQueryWrapper<SwapRequest>()
                        .eq(SwapRequest::getBatchId, request.getBatchId())
                        .eq(SwapRequest::getStudentAId, request.getStudentAId())
                        .in(SwapRequest::getStatus, "PENDING_B_CONFIRM", "PENDING_MANAGER"));
        if (aPending > 0) throw new BusinessException("您已有一条进行中的互换申请");

        // 检查B是否已被其他互换申请指定为目标
        Long bPending = swapRequestMapper.selectCount(
                new LambdaQueryWrapper<SwapRequest>()
                        .eq(SwapRequest::getBatchId, request.getBatchId())
                        .eq(SwapRequest::getStudentBId, request.getStudentBId())
                        .in(SwapRequest::getStatus, "PENDING_B_CONFIRM", "PENDING_MANAGER"));
        if (bPending > 0) throw new BusinessException("目标学生已有进行中的互换申请");

        // 验证双方同性別楼栋
        String genderA = dormRoomService.resolveBuildingGender(resultA.getRoomId());
        String genderB = dormRoomService.resolveBuildingGender(resultB.getRoomId());
        if (genderA == null || genderB == null || !genderA.equals(genderB)) {
            throw new BusinessException("双方宿舍楼性别类型不一致，无法互换");
        }

        SwapRequest swap = new SwapRequest();
        swap.setBatchId(request.getBatchId());
        swap.setStudentAId(request.getStudentAId());
        swap.setStudentARoomId(resultA.getRoomId());
        swap.setStudentBId(request.getStudentBId());
        swap.setStudentBRoomId(resultB.getRoomId());
        swap.setInitiatorRemark(request.getRemark());
        swap.setStatus("PENDING_B_CONFIRM");
        swapRequestMapper.insert(swap);
        return toVO(swap);
    }

    @Transactional
    public SwapRequestVO confirmSwapRequest(Long requestId, Long studentBId) {
        SwapRequest swap = swapRequestMapper.selectById(requestId);
        if (swap == null) throw new BusinessException("互换申请不存在");
        if (!"PENDING_B_CONFIRM".equals(swap.getStatus())) {
            throw new BusinessException("该申请当前状态不可确认");
        }
        if (!swap.getStudentBId().equals(studentBId)) {
            throw new BusinessException("只有被指定的学生B才能确认");
        }
        swap.setStatus("PENDING_MANAGER");
        swap.setBConfirmStatus("CONFIRMED");
        swapRequestMapper.updateById(swap);
        return toVO(swap);
    }

    @Transactional
    public SwapRequestVO rejectSwapRequest(Long requestId, Long studentBId) {
        SwapRequest swap = swapRequestMapper.selectById(requestId);
        if (swap == null) throw new BusinessException("互换申请不存在");
        if (!"PENDING_B_CONFIRM".equals(swap.getStatus())) {
            throw new BusinessException("该申请当前状态不可拒绝");
        }
        if (!swap.getStudentBId().equals(studentBId)) {
            throw new BusinessException("只有被指定的学生B才能拒绝");
        }
        swap.setStatus("REJECTED_BY_B");
        swap.setBConfirmStatus("REJECTED");
        swapRequestMapper.updateById(swap);
        return toVO(swap);
    }

    @Transactional
    public SwapRequestVO cancelSwapRequest(Long requestId, Long studentAId) {
        SwapRequest swap = swapRequestMapper.selectById(requestId);
        if (swap == null) throw new BusinessException("互换申请不存在");
        if (!swap.getStudentAId().equals(studentAId)) {
            throw new BusinessException("只有发起人才能取消申请");
        }
        if (!"PENDING_B_CONFIRM".equals(swap.getStatus())) {
            throw new BusinessException("只能在B确认前取消申请");
        }
        swap.setStatus("CANCELLED");
        swapRequestMapper.updateById(swap);
        return toVO(swap);
    }

    @Transactional
    public SwapRequestVO handleSwapRequest(HandleSwapRequest request) {
        SwapRequest swap = swapRequestMapper.selectById(request.getRequestId());
        if (swap == null) throw new BusinessException("互换申请不存在");
        if (!"PENDING_MANAGER".equals(swap.getStatus())) {
            throw new BusinessException("该申请当前状态不可处理");
        }
        if (!"APPROVED".equals(request.getStatus()) && !"REJECTED_BY_MANAGER".equals(request.getStatus())) {
            throw new BusinessException("处理结果只能为 APPROVED 或 REJECTED_BY_MANAGER");
        }

        swap.setStatus(request.getStatus());
        swap.setHandlerId(request.getHandlerId());
        swap.setHandleRemark(request.getHandleRemark());
        swap.setHandleTime(LocalDateTime.now());
        swapRequestMapper.updateById(swap);

        if ("APPROVED".equals(request.getStatus())) {
            AssignmentResult resultA = resultMapper.selectOne(
                    new LambdaQueryWrapper<AssignmentResult>()
                            .eq(AssignmentResult::getBatchId, swap.getBatchId())
                            .eq(AssignmentResult::getStudentId, swap.getStudentAId()));
            AssignmentResult resultB = resultMapper.selectOne(
                    new LambdaQueryWrapper<AssignmentResult>()
                            .eq(AssignmentResult::getBatchId, swap.getBatchId())
                            .eq(AssignmentResult::getStudentId, swap.getStudentBId()));

            if (resultA == null || resultB == null) {
                throw new BusinessException("学生分配记录已变更，无法完成互换");
            }

            Long tempRoomId = resultA.getRoomId();
            Integer tempBedNo = resultA.getBedNo();
            resultA.setRoomId(resultB.getRoomId());
            resultA.setBedNo(resultB.getBedNo());
            resultB.setRoomId(tempRoomId);
            resultB.setBedNo(tempBedNo);

            resultMapper.updateById(resultA);
            resultMapper.updateById(resultB);
        }

        return toVO(swap);
    }

    public List<SwapRequestVO> listMyRequests(Long studentId) {
        return swapRequestMapper.selectList(
                new LambdaQueryWrapper<SwapRequest>()
                        .and(w -> w.eq(SwapRequest::getStudentAId, studentId)
                                .or().eq(SwapRequest::getStudentBId, studentId))
                        .orderByDesc(SwapRequest::getId))
                .stream().map(this::toVO).toList();
    }

    public List<SwapRequestVO> listIncomingForB(Long studentBId) {
        return swapRequestMapper.selectList(
                new LambdaQueryWrapper<SwapRequest>()
                        .eq(SwapRequest::getStudentBId, studentBId)
                        .eq(SwapRequest::getStatus, "PENDING_B_CONFIRM")
                        .orderByDesc(SwapRequest::getId))
                .stream().map(this::toVO).toList();
    }

    public List<SwapRequestVO> listPendingForManager(Long managerId) {
        List<DormRoomVO> myRoomVos = dormRoomService.listRoomsByManager(managerId);
        if (myRoomVos.isEmpty()) return List.of();

        List<Long> roomIds = myRoomVos.stream().map(DormRoomVO::getId).toList();
        return swapRequestMapper.selectList(
                new LambdaQueryWrapper<SwapRequest>()
                        .eq(SwapRequest::getStatus, "PENDING_MANAGER")
                        .and(w -> w.in(SwapRequest::getStudentARoomId, roomIds)
                                .or().in(SwapRequest::getStudentBRoomId, roomIds))
                        .orderByAsc(SwapRequest::getId))
                .stream().map(this::toVO).toList();
    }

    private SwapRequestVO toVO(SwapRequest swap) {
        SwapRequestVO vo = new SwapRequestVO();
        vo.setId(swap.getId());
        vo.setBatchId(swap.getBatchId());
        vo.setInitiatorRemark(swap.getInitiatorRemark());
        vo.setStatus(swap.getStatus());
        vo.setBConfirmStatus(swap.getBConfirmStatus());
        vo.setHandleRemark(swap.getHandleRemark());
        vo.setHandleTime(swap.getHandleTime());
        vo.setCreateTime(swap.getCreateTime());

        // Student A
        vo.setStudentAId(swap.getStudentAId());
        User userA = userMapper.selectById(swap.getStudentAId());
        if (userA != null) {
            vo.setStudentAName(userA.getRealName());
            vo.setStudentANo(userA.getUserNo());
        }
        DormRoom roomA = dormRoomMapper.selectById(swap.getStudentARoomId());
        if (roomA != null) {
            vo.setStudentABuilding(roomA.getBuilding());
            vo.setStudentARoomNo(dormRoomService.formatRoomDisplay(roomA));
        }

        // Student B
        vo.setStudentBId(swap.getStudentBId());
        User userB = userMapper.selectById(swap.getStudentBId());
        if (userB != null) {
            vo.setStudentBName(userB.getRealName());
            vo.setStudentBNo(userB.getUserNo());
        }
        DormRoom roomB = dormRoomMapper.selectById(swap.getStudentBRoomId());
        if (roomB != null) {
            vo.setStudentBBuilding(roomB.getBuilding());
            vo.setStudentBRoomNo(dormRoomService.formatRoomDisplay(roomB));
        }

        return vo;
    }
}
