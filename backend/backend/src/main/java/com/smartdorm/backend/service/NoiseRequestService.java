package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.DormRoomVO;
import com.smartdorm.backend.vo.NoiseRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NoiseRequestService {

    private final NoiseRequestMapper noiseRequestMapper;
    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final DormRoomMapper dormRoomMapper;
    private final DormFloorMapper dormFloorMapper;
    private final DormManagerScopeMapper dormManagerScopeMapper;
    private final UserMapper userMapper;
    private final DormRoomService dormRoomService;
    private final NotifyInboxService notifyInboxService;

    private Long requirePublishedBatch(Long batchId) {
        AssignmentBatch b = batchMapper.selectById(batchId);
        if (b == null) throw new BusinessException("分配批次不存在");
        if (!"PUBLISHED".equals(b.getStatus())) throw new BusinessException("仅支持已公示批次");
        return b.getId();
    }

    private Long latestPublishedBatchId() {
        AssignmentBatch latest = batchMapper.selectOne(new LambdaQueryWrapper<AssignmentBatch>()
                .eq(AssignmentBatch::getStatus, "PUBLISHED")
                .orderByDesc(AssignmentBatch::getId)
                .last("LIMIT 1"));
        if (latest == null) throw new BusinessException("暂无已公示批次");
        return latest.getId();
    }

    private Long roomIdOfStudent(Long batchId, Long studentId) {
        AssignmentResult r = resultMapper.selectOne(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getStudentId, studentId)
                .last("LIMIT 1"));
        if (r == null) throw new BusinessException("未找到学生分配记录");
        return r.getRoomId();
    }

    private boolean isStudentInRoom(Long batchId, Long roomId, Long studentId) {
        Long c = resultMapper.selectCount(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getRoomId, roomId)
                .eq(AssignmentResult::getStudentId, studentId));
        return c != null && c > 0;
    }

    private String roomDisplay(Long roomId) {
        DormRoom room = dormRoomService.getOrThrow(roomId);
        DormRoomVO vo = dormRoomService.toVO(room);
        String buildingName = vo.getBuildingName() == null ? "" : vo.getBuildingName();
        return buildingName + " " + dormRoomService.formatRoomDisplay(room);
    }

    private Set<Long> studentsInRoom(Long batchId, Long roomId) {
        return resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, batchId)
                        .eq(AssignmentResult::getRoomId, roomId))
                .stream().map(AssignmentResult::getStudentId).collect(java.util.stream.Collectors.toSet());
    }

    private Set<Long> managersForRoom(Long roomId) {
        DormRoom room = dormRoomMapper.selectById(roomId);
        if (room == null) return Set.of();
        DormFloor floor = dormFloorMapper.selectById(room.getFloorId());
        if (floor == null) return Set.of();
        Long floorId = floor.getId();
        Long buildingId = floor.getBuildingId();

        return dormManagerScopeMapper.selectList(new LambdaQueryWrapper<DormManagerScope>()
                        .and(w -> w.eq(DormManagerScope::getFloorId, floorId)
                                .or().eq(DormManagerScope::getBuildingId, buildingId)))
                .stream().map(DormManagerScope::getManagerId).collect(java.util.stream.Collectors.toSet());
    }

    private void requireManagerCanHandleRoom(Long managerId, Long roomId) {
        List<DormRoomVO> rooms = dormRoomService.listRoomsByManager(managerId);
        boolean ok = rooms.stream().anyMatch(r -> roomId.equals(r.getId()));
        if (!ok) throw new BusinessException("无权处理该宿舍相关请求");
    }

    @Transactional
    public NoiseRequestVO create(Long batchId, Long fromStudentId, Long toRoomId, String content) {
        Long bid = requirePublishedBatch(batchId);
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw new BusinessException("提醒内容不能为空");
        if (c.length() > 255) throw new BusinessException("提醒内容过长");

        Long fromRoomId = roomIdOfStudent(bid, fromStudentId);
        if (fromRoomId.equals(toRoomId)) throw new BusinessException("目标宿舍不能是自己宿舍");

        DormRoom target = dormRoomMapper.selectById(toRoomId);
        if (target == null) throw new BusinessException("目标宿舍不存在");

        LocalDateTime tenMinAgo = LocalDateTime.now().minusMinutes(10);
        Long recent = noiseRequestMapper.selectCount(new LambdaQueryWrapper<NoiseRequest>()
                .eq(NoiseRequest::getBatchId, bid)
                .eq(NoiseRequest::getFromRoomId, fromRoomId)
                .eq(NoiseRequest::getToRoomId, toRoomId)
                .ge(NoiseRequest::getCreateTime, tenMinAgo)
                .ne(NoiseRequest::getStatus, "CANCELLED"));
        if (recent != null && recent > 0) throw new BusinessException("操作过于频繁，请稍后再试");

        NoiseRequest row = new NoiseRequest();
        row.setBatchId(bid);
        row.setFromRoomId(fromRoomId);
        row.setToRoomId(toRoomId);
        row.setContent(c);
        row.setStatus("PENDING");
        noiseRequestMapper.insert(row);

        Long id = row.getId();
        Set<Long> recipients = new HashSet<>();
        recipients.addAll(studentsInRoom(bid, fromRoomId));
        recipients.addAll(studentsInRoom(bid, toRoomId));
        recipients.addAll(managersForRoom(toRoomId));
        notifyInboxService.pushToUsers(recipients, "NOISE_REQUEST", id, "安静请求", c);

        return toVO(row);
    }

    public List<NoiseRequestVO> listMy(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long myRoomId = roomIdOfStudent(bid, studentId);
        return noiseRequestMapper.selectList(new LambdaQueryWrapper<NoiseRequest>()
                        .eq(NoiseRequest::getBatchId, bid)
                        .eq(NoiseRequest::getFromRoomId, myRoomId)
                        .orderByDesc(NoiseRequest::getId)
                        .last("LIMIT 100"))
                .stream().map(this::toVO).toList();
    }

    public List<NoiseRequestVO> listIncoming(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long myRoomId = roomIdOfStudent(bid, studentId);
        return noiseRequestMapper.selectList(new LambdaQueryWrapper<NoiseRequest>()
                        .eq(NoiseRequest::getBatchId, bid)
                        .eq(NoiseRequest::getToRoomId, myRoomId)
                        .orderByDesc(NoiseRequest::getId)
                        .last("LIMIT 100"))
                .stream().map(this::toVO).toList();
    }

    public List<NoiseRequestVO> listForManager(Long managerId) {
        Long bid = latestPublishedBatchId();
        List<DormRoomVO> myRooms = dormRoomService.listRoomsByManager(managerId);
        if (myRooms.isEmpty()) return List.of();
        List<Long> roomIds = myRooms.stream().map(DormRoomVO::getId).toList();
        return noiseRequestMapper.selectList(new LambdaQueryWrapper<NoiseRequest>()
                        .eq(NoiseRequest::getBatchId, bid)
                        .in(NoiseRequest::getToRoomId, roomIds)
                        .orderByDesc(NoiseRequest::getId)
                        .last("LIMIT 200"))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public NoiseRequestVO ack(Long id, Long studentId) {
        NoiseRequest row = noiseRequestMapper.selectById(id);
        if (row == null) throw new BusinessException("请求不存在");
        if (!isStudentInRoom(row.getBatchId(), row.getToRoomId(), studentId)) {
            throw new BusinessException("无权操作该请求");
        }
        if ("CANCELLED".equals(row.getStatus()) || "RESOLVED".equals(row.getStatus())) {
            return toVO(row);
        }
        if (!"ACKED".equals(row.getStatus())) {
            row.setStatus("ACKED");
            row.setAckTime(LocalDateTime.now());
            noiseRequestMapper.updateById(row);
            Set<Long> notify = studentsInRoom(row.getBatchId(), row.getFromRoomId());
            notifyInboxService.pushToUsers(notify, "NOISE_REQUEST", row.getId(), "安静请求已知悉", row.getContent());
        }
        return toVO(noiseRequestMapper.selectById(id));
    }

    @Transactional
    public NoiseRequestVO escalate(Long id, Long studentId) {
        NoiseRequest row = noiseRequestMapper.selectById(id);
        if (row == null) throw new BusinessException("请求不存在");
        if (!isStudentInRoom(row.getBatchId(), row.getFromRoomId(), studentId)) {
            throw new BusinessException("无权操作该请求");
        }
        if ("CANCELLED".equals(row.getStatus()) || "RESOLVED".equals(row.getStatus())) {
            return toVO(row);
        }
        if (!"ESCALATED".equals(row.getStatus())) {
            row.setStatus("ESCALATED");
            row.setEscalateTime(LocalDateTime.now());
            noiseRequestMapper.updateById(row);
            Set<Long> managers = managersForRoom(row.getToRoomId());
            notifyInboxService.pushToUsers(managers, "NOISE_REQUEST", row.getId(), "噪音提醒升级处理", row.getContent());
        }
        return toVO(noiseRequestMapper.selectById(id));
    }

    @Transactional
    public NoiseRequestVO resolve(Long id, Long managerId, String remark) {
        NoiseRequest row = noiseRequestMapper.selectById(id);
        if (row == null) throw new BusinessException("请求不存在");
        requireManagerCanHandleRoom(managerId, row.getToRoomId());
        if ("CANCELLED".equals(row.getStatus())) {
            return toVO(row);
        }
        row.setStatus("RESOLVED");
        row.setHandlerId(managerId);
        row.setHandleRemark(remark == null || remark.isBlank() ? null : remark.trim());
        row.setHandleTime(LocalDateTime.now());
        noiseRequestMapper.updateById(row);

        Set<Long> notify = new HashSet<>();
        notify.addAll(studentsInRoom(row.getBatchId(), row.getFromRoomId()));
        notify.addAll(studentsInRoom(row.getBatchId(), row.getToRoomId()));
        notifyInboxService.pushToUsers(notify, "NOISE_REQUEST", row.getId(), "噪音提醒已处理", row.getHandleRemark());
        return toVO(noiseRequestMapper.selectById(id));
    }

    @Transactional
    public NoiseRequestVO cancel(Long id, Long studentId) {
        NoiseRequest row = noiseRequestMapper.selectById(id);
        if (row == null) throw new BusinessException("请求不存在");
        if (!isStudentInRoom(row.getBatchId(), row.getFromRoomId(), studentId)) {
            throw new BusinessException("无权操作该请求");
        }
        if ("RESOLVED".equals(row.getStatus())) return toVO(row);
        row.setStatus("CANCELLED");
        noiseRequestMapper.updateById(row);
        return toVO(noiseRequestMapper.selectById(id));
    }

    private NoiseRequestVO toVO(NoiseRequest row) {
        NoiseRequestVO vo = new NoiseRequestVO();
        vo.setId(row.getId());
        vo.setBatchId(row.getBatchId());
        vo.setFromRoomId(row.getFromRoomId());
        vo.setToRoomId(row.getToRoomId());
        vo.setFromRoomDisplay(roomDisplay(row.getFromRoomId()));
        vo.setToRoomDisplay(roomDisplay(row.getToRoomId()));
        vo.setContent(row.getContent());
        vo.setStatus(row.getStatus());
        vo.setAckTime(row.getAckTime());
        vo.setEscalateTime(row.getEscalateTime());
        vo.setHandleRemark(row.getHandleRemark());
        vo.setHandleTime(row.getHandleTime());
        vo.setCreateTime(row.getCreateTime());
        if (row.getHandlerId() != null) {
            User u = userMapper.selectById(row.getHandlerId());
            vo.setHandlerName(u != null ? u.getRealName() : null);
        }
        return vo;
    }
}
