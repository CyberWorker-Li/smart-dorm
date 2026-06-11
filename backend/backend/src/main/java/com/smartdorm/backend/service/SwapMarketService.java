package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SwapMarketService {

    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final DormRoomMapper dormRoomMapper;
    private final DormFloorMapper dormFloorMapper;
    private final DormBuildingMapper dormBuildingMapper;
    private final SysUserStudentMapper sysUserStudentMapper;
    private final UserMapper userMapper;

    private final SwapIntentMapper swapIntentMapper;
    private final SwapChatThreadMapper swapChatThreadMapper;
    private final SwapChatMessageMapper swapChatMessageMapper;

    public Long resolveBatchIdOrLatestPublished(Long batchId) {
        if (batchId != null) {
            AssignmentBatch b = batchMapper.selectById(batchId);
            if (b == null) throw new BusinessException("分配批次不存在");
            if (!"PUBLISHED".equals(b.getStatus())) throw new BusinessException("仅支持查看已公示批次");
            return b.getId();
        }
        AssignmentBatch latest = batchMapper.selectOne(new LambdaQueryWrapper<AssignmentBatch>()
                .eq(AssignmentBatch::getStatus, "PUBLISHED")
                .orderByDesc(AssignmentBatch::getId)
                .last("LIMIT 1"));
        if (latest == null) throw new BusinessException("暂无已公示的分配批次");
        return latest.getId();
    }

    private String aliasFor(Long studentId) {
        long s = studentId == null ? 0 : studentId;
        return "匿名" + String.format("%04d", Math.floorMod(s, 10000));
    }

    public List<SwapMarketRoomVO> listMarketRooms(Long batchId, String gender) {
        Long bid = resolveBatchIdOrLatestPublished(batchId);

        List<AssignmentResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, bid));
        Map<Long, Long> studentToRoom = results.stream()
                .collect(Collectors.toMap(AssignmentResult::getStudentId, AssignmentResult::getRoomId, (a, b) -> a));
        Map<Long, Integer> occByRoom = new HashMap<>();
        for (AssignmentResult r : results) {
            occByRoom.merge(r.getRoomId(), 1, Integer::sum);
        }

        List<DormRoom> rooms = dormRoomMapper.selectList(null);
        Map<Long, DormFloor> floorById = dormFloorMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormFloor::getId, f -> f, (a, b) -> a));
        Map<Long, DormBuilding> buildingById = dormBuildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b, (a, b) -> a));

        String genderNorm = gender != null && !gender.isBlank() ? gender.trim().toUpperCase() : null;
        if (genderNorm != null && !"MALE".equals(genderNorm) && !"FEMALE".equals(genderNorm)) {
            genderNorm = null;
        }

        List<SwapIntent> openIntents = swapIntentMapper.selectList(new LambdaQueryWrapper<SwapIntent>()
                .eq(SwapIntent::getBatchId, bid)
                .eq(SwapIntent::getStatus, "OPEN")
                .orderByDesc(SwapIntent::getId));
        Map<Long, List<SwapIntent>> intentsByRoom = new HashMap<>();
        for (SwapIntent it : openIntents) {
            Long rid = studentToRoom.get(it.getStudentId());
            if (rid != null) {
                intentsByRoom.computeIfAbsent(rid, k -> new ArrayList<>()).add(it);
            }
        }

        List<SwapMarketRoomVO> out = new ArrayList<>();
        for (DormRoom r : rooms) {
            DormFloor f = floorById.get(r.getFloorId());
            DormBuilding b = f != null ? buildingById.get(f.getBuildingId()) : null;
            if (f == null || b == null) continue;
            if (!Boolean.TRUE.equals(b.getValid()) || !Boolean.TRUE.equals(f.getValid()) || !Boolean.TRUE.equals(r.getValid())) {
                continue;
            }
            if (genderNorm != null && !genderNorm.equalsIgnoreCase(b.getGender())) {
                continue;
            }

            int cap = r.getCapacity() == null ? 0 : r.getCapacity();
            int occ = occByRoom.getOrDefault(r.getId(), 0);
            int vacancies = Math.max(0, cap - occ);
            List<SwapIntent> its = intentsByRoom.getOrDefault(r.getId(), List.of());
            boolean hasIntent = !its.isEmpty();
            if (vacancies <= 0 && !hasIntent) {
                continue;
            }

            SwapMarketRoomVO vo = new SwapMarketRoomVO();
            vo.setBatchId(bid);
            vo.setRoomId(r.getId());
            vo.setBuildingName(b.getName());
            vo.setFloorNo(f.getFloorNo());
            vo.setRoomNo(r.getRoomNo());
            vo.setGender(b.getGender());
            vo.setCapacity(cap);
            vo.setOccupied(occ);
            vo.setVacancies(vacancies);
            vo.setHasSwapIntent(hasIntent);
            if (hasIntent) {
                List<SwapMarketUserIntentVO> rows = its.stream().map(it -> {
                    SwapMarketUserIntentVO uvo = new SwapMarketUserIntentVO();
                    uvo.setStudentId(it.getStudentId());
                    uvo.setAlias(aliasFor(it.getStudentId()));
                    uvo.setRemark(it.getRemark());
                    return uvo;
                }).toList();
                vo.setIntents(rows);
            } else {
                vo.setIntents(List.of());
            }
            out.add(vo);
        }

        out.sort(Comparator
                .comparingInt((SwapMarketRoomVO v) -> v.getVacancies() != null ? -v.getVacancies() : 0)
                .thenComparing(v -> Boolean.TRUE.equals(v.getHasSwapIntent()) ? 0 : 1)
                .thenComparingLong(v -> v.getRoomId() != null ? v.getRoomId() : Long.MAX_VALUE));
        return out;
    }

    public SwapMarketMyIntentVO getMyIntent(Long batchId, Long studentId) {
        Long bid = resolveBatchIdOrLatestPublished(batchId);
        SwapIntent it = swapIntentMapper.selectOne(new LambdaQueryWrapper<SwapIntent>()
                .eq(SwapIntent::getBatchId, bid)
                .eq(SwapIntent::getStudentId, studentId)
                .last("LIMIT 1"));
        SwapMarketMyIntentVO vo = new SwapMarketMyIntentVO();
        vo.setBatchId(bid);
        vo.setStudentId(studentId);
        if (it == null) {
            vo.setStatus("NONE");
            vo.setRemark(null);
        } else {
            vo.setStatus(it.getStatus());
            vo.setRemark(it.getRemark());
        }
        return vo;
    }

    @Transactional
    public SwapMarketMyIntentVO upsertIntent(Long batchId, Long studentId, String remark) {
        Long bid = resolveBatchIdOrLatestPublished(batchId);
        User u = userMapper.selectById(studentId);
        if (u == null || !"STUDENT".equals(u.getUserType())) {
            throw new BusinessException("仅学生可发布换宿意愿");
        }

        SwapIntent it = swapIntentMapper.selectOne(new LambdaQueryWrapper<SwapIntent>()
                .eq(SwapIntent::getBatchId, bid)
                .eq(SwapIntent::getStudentId, studentId)
                .last("LIMIT 1"));
        if (it == null) {
            it = new SwapIntent();
            it.setBatchId(bid);
            it.setStudentId(studentId);
            it.setRemark(remark != null && !remark.isBlank() ? remark.trim() : null);
            it.setStatus("OPEN");
            swapIntentMapper.insert(it);
        } else {
            it.setRemark(remark != null && !remark.isBlank() ? remark.trim() : null);
            it.setStatus("OPEN");
            swapIntentMapper.updateById(it);
        }
        return getMyIntent(bid, studentId);
    }

    @Transactional
    public SwapMarketMyIntentVO closeIntent(Long batchId, Long studentId) {
        Long bid = resolveBatchIdOrLatestPublished(batchId);
        SwapIntent it = swapIntentMapper.selectOne(new LambdaQueryWrapper<SwapIntent>()
                .eq(SwapIntent::getBatchId, bid)
                .eq(SwapIntent::getStudentId, studentId)
                .last("LIMIT 1"));
        if (it != null) {
            it.setStatus("CLOSED");
            swapIntentMapper.updateById(it);
        }
        return getMyIntent(bid, studentId);
    }

    private Long pickLeaderOrAnyStudentInRoom(Long batchId, Long roomId) {
        List<AssignmentResult> inRoom = resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getRoomId, roomId)
                .orderByAsc(AssignmentResult::getBedNo, AssignmentResult::getId));
        if (inRoom.isEmpty()) return null;
        List<Long> ids = inRoom.stream().map(AssignmentResult::getStudentId).toList();
        List<SysUserStudent> profiles = sysUserStudentMapper.selectList(
                new LambdaQueryWrapper<SysUserStudent>().in(SysUserStudent::getUserId, ids));
        Set<Long> leaders = profiles.stream()
                .filter(p -> Boolean.TRUE.equals(p.getLeader()))
                .map(SysUserStudent::getUserId)
                .collect(Collectors.toSet());
        for (AssignmentResult r : inRoom) {
            if (leaders.contains(r.getStudentId())) {
                return r.getStudentId();
            }
        }
        return inRoom.get(0).getStudentId();
    }

    private void requireBothInBatch(Long batchId, Long a, Long b) {
        Long ca = resultMapper.selectCount(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getStudentId, a));
        if (ca == null || ca == 0) throw new BusinessException("发起人不在该批次分配结果中");
        Long cb = resultMapper.selectCount(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getStudentId, b));
        if (cb == null || cb == 0) throw new BusinessException("对方不在该批次分配结果中");
    }

    @Transactional
    public Long startChat(Long batchId, Long studentId, Long targetStudentId, Long targetRoomId) {
        Long bid = resolveBatchIdOrLatestPublished(batchId);
        if (targetStudentId == null) {
            if (targetRoomId == null) {
                throw new BusinessException("请选择目标同学或目标宿舍");
            }
            targetStudentId = pickLeaderOrAnyStudentInRoom(bid, targetRoomId);
            if (targetStudentId == null) {
                throw new BusinessException("目标宿舍暂无在住学生，无法沟通");
            }
        }
        if (studentId.equals(targetStudentId)) throw new BusinessException("不能和自己发起沟通");

        Long targetId = targetStudentId;
        requireBothInBatch(bid, studentId, targetId);

        SwapChatThread existing = swapChatThreadMapper.selectOne(new LambdaQueryWrapper<SwapChatThread>()
                .eq(SwapChatThread::getBatchId, bid)
                .and(w -> w.eq(SwapChatThread::getStarterId, studentId).eq(SwapChatThread::getTargetStudentId, targetId)
                        .or()
                        .eq(SwapChatThread::getStarterId, targetId).eq(SwapChatThread::getTargetStudentId, studentId))
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }

        SwapChatThread t = new SwapChatThread();
        t.setBatchId(bid);
        t.setStarterId(studentId);
        t.setTargetStudentId(targetId);
        t.setStatus("OPEN");
        swapChatThreadMapper.insert(t);
        return t.getId();
    }

    private SwapChatThread getThreadOrThrow(Long threadId) {
        SwapChatThread t = swapChatThreadMapper.selectById(threadId);
        if (t == null) throw new BusinessException("会话不存在");
        return t;
    }

    private void requireMember(SwapChatThread t, Long studentId) {
        if (!studentId.equals(t.getStarterId()) && !studentId.equals(t.getTargetStudentId())) {
            throw new BusinessException("无权访问该会话");
        }
    }

    public List<SwapChatThreadVO> listThreads(Long studentId) {
        List<SwapChatThread> threads = swapChatThreadMapper.selectList(new LambdaQueryWrapper<SwapChatThread>()
                .and(w -> w.eq(SwapChatThread::getStarterId, studentId).or().eq(SwapChatThread::getTargetStudentId, studentId))
                .orderByDesc(SwapChatThread::getUpdateTime, SwapChatThread::getId));

        List<SwapChatThreadVO> out = new ArrayList<>();
        for (SwapChatThread t : threads) {
            Long other = studentId.equals(t.getStarterId()) ? t.getTargetStudentId() : t.getStarterId();
            SwapChatThreadVO vo = new SwapChatThreadVO();
            vo.setThreadId(t.getId());
            vo.setBatchId(t.getBatchId());
            vo.setOtherStudentId(other);
            vo.setOtherAlias(aliasFor(other));

            SwapChatMessage last = swapChatMessageMapper.selectOne(new LambdaQueryWrapper<SwapChatMessage>()
                    .eq(SwapChatMessage::getThreadId, t.getId())
                    .orderByDesc(SwapChatMessage::getId)
                    .last("LIMIT 1"));
            if (last != null) {
                vo.setLastMessage(last.getContent());
                vo.setLastTime(last.getCreateTime());
            }
            out.add(vo);
        }
        out.sort(Comparator.comparing((SwapChatThreadVO v) -> v.getLastTime() != null ? v.getLastTime() : LocalDateTime.MIN).reversed());
        return out;
    }

    public List<SwapChatMessageVO> listMessages(Long threadId, Long studentId) {
        SwapChatThread t = getThreadOrThrow(threadId);
        requireMember(t, studentId);

        List<SwapChatMessage> rows = swapChatMessageMapper.selectList(new LambdaQueryWrapper<SwapChatMessage>()
                .eq(SwapChatMessage::getThreadId, threadId)
                .orderByAsc(SwapChatMessage::getId));
        return rows.stream().map(m -> {
            SwapChatMessageVO vo = new SwapChatMessageVO();
            vo.setId(m.getId());
            vo.setMine(studentId.equals(m.getSenderId()));
            vo.setContent(m.getContent());
            vo.setTime(m.getCreateTime());
            return vo;
        }).toList();
    }

    @Transactional
    public SwapChatMessageVO sendMessage(Long threadId, Long studentId, String content) {
        SwapChatThread t = getThreadOrThrow(threadId);
        requireMember(t, studentId);
        if (!"OPEN".equals(t.getStatus())) throw new BusinessException("会话已关闭");

        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw new BusinessException("消息不能为空");
        if (c.length() > 500) throw new BusinessException("消息过长");

        SwapChatMessage m = new SwapChatMessage();
        m.setThreadId(threadId);
        m.setSenderId(studentId);
        m.setContent(c);
        swapChatMessageMapper.insert(m);

        t.setUpdateTime(LocalDateTime.now());
        swapChatThreadMapper.updateById(t);

        SwapChatMessageVO vo = new SwapChatMessageVO();
        vo.setId(m.getId());
        vo.setMine(true);
        vo.setContent(m.getContent());
        vo.setTime(m.getCreateTime());
        return vo;
    }
}
