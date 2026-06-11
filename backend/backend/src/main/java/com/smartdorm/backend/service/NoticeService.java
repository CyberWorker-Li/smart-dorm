package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.NoticeStatsVO;
import com.smartdorm.backend.vo.NoticeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final DormFloorMapper dormFloorMapper;
    private final DormRoomMapper dormRoomMapper;
    private final NotifyInboxMapper notifyInboxMapper;
    private final NotifyInboxService notifyInboxService;
    private final UserMapper userMapper;

    private Long latestPublishedBatchId() {
        AssignmentBatch latest = batchMapper.selectOne(new LambdaQueryWrapper<AssignmentBatch>()
                .eq(AssignmentBatch::getStatus, "PUBLISHED")
                .orderByDesc(AssignmentBatch::getId)
                .last("LIMIT 1"));
        if (latest == null) throw new BusinessException("暂无已公示批次");
        return latest.getId();
    }

    @Transactional
    public NoticeVO createDraft(Long publisherId, String title, String content, String scopeType, Long buildingId, Long floorId) {
        User u = userMapper.selectById(publisherId);
        if (u == null || !UserType.DORM_MANAGER.name().equals(u.getUserType())) {
            throw new BusinessException("仅宿管可发布通知");
        }
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new BusinessException("标题不能为空");
        if (t.length() > 80) throw new BusinessException("标题过长");

        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw new BusinessException("内容不能为空");

        String st = scopeType == null ? "" : scopeType.trim().toUpperCase();
        if (!"ALL".equals(st) && !"BUILDING".equals(st) && !"FLOOR".equals(st)) {
            throw new BusinessException("范围类型非法");
        }
        if ("BUILDING".equals(st) && buildingId == null) throw new BusinessException("请选择楼栋");
        if ("FLOOR".equals(st) && floorId == null) throw new BusinessException("请选择楼层");

        Notice n = new Notice();
        n.setPublisherId(publisherId);
        n.setTitle(t);
        n.setContent(c);
        n.setScopeType(st);
        n.setBuildingId("BUILDING".equals(st) ? buildingId : null);
        n.setFloorId("FLOOR".equals(st) ? floorId : null);
        n.setStatus("DRAFT");
        noticeMapper.insert(n);
        return toVO(n);
    }

    public List<NoticeVO> listForManager(Long managerId, String status) {
        LambdaQueryWrapper<Notice> w = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getPublisherId, managerId)
                .orderByDesc(Notice::getId);
        if (status != null && !status.isBlank()) {
            w.eq(Notice::getStatus, status.trim().toUpperCase());
        }
        return noticeMapper.selectList(w).stream().map(this::toVO).toList();
    }

    public NoticeVO getDetail(Long id) {
        Notice n = noticeMapper.selectById(id);
        if (n == null) throw new BusinessException("通知不存在");
        return toVO(n);
    }

    @Transactional
    public NoticeVO publish(Long id, Long managerId) {
        Notice n = noticeMapper.selectById(id);
        if (n == null) throw new BusinessException("通知不存在");
        if (!managerId.equals(n.getPublisherId())) throw new BusinessException("无权发布该通知");
        if ("PUBLISHED".equals(n.getStatus())) return toVO(n);

        n.setStatus("PUBLISHED");
        n.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(n);

        Long bid = latestPublishedBatchId();
        Set<Long> recipients = resolveRecipients(bid, n.getScopeType(), n.getBuildingId(), n.getFloorId());
        String summary = n.getContent();
        if (summary != null && summary.length() > 255) summary = summary.substring(0, 255);
        notifyInboxService.pushToUsers(recipients, "NOTICE", n.getId(), n.getTitle(), summary);
        return toVO(noticeMapper.selectById(id));
    }

    public NoticeStatsVO stats(Long noticeId, Long managerId) {
        Notice n = noticeMapper.selectById(noticeId);
        if (n == null) throw new BusinessException("通知不存在");
        if (!managerId.equals(n.getPublisherId())) throw new BusinessException("无权查看");

        Long total = notifyInboxMapper.selectCount(new LambdaQueryWrapper<NotifyInbox>()
                .eq(NotifyInbox::getBizType, "NOTICE")
                .eq(NotifyInbox::getBizId, noticeId));
        Long read = notifyInboxMapper.selectCount(new LambdaQueryWrapper<NotifyInbox>()
                .eq(NotifyInbox::getBizType, "NOTICE")
                .eq(NotifyInbox::getBizId, noticeId)
                .isNotNull(NotifyInbox::getReadTime));

        long t = total == null ? 0 : total;
        long r = read == null ? 0 : read;
        NoticeStatsVO vo = new NoticeStatsVO();
        vo.setTotalRecipients(t);
        vo.setReadCount(r);
        vo.setUnreadCount(Math.max(0, t - r));
        return vo;
    }

    private Set<Long> resolveRecipients(Long batchId, String scopeType, Long buildingId, Long floorId) {
        Set<Long> roomIds = new HashSet<>();
        if ("ALL".equals(scopeType)) {
            resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId))
                    .forEach(r -> roomIds.add(r.getRoomId()));
        } else if ("BUILDING".equals(scopeType)) {
            List<DormFloor> floors = dormFloorMapper.selectList(new LambdaQueryWrapper<DormFloor>()
                    .eq(DormFloor::getBuildingId, buildingId));
            if (!floors.isEmpty()) {
                List<Long> floorIds = floors.stream().map(DormFloor::getId).toList();
                dormRoomMapper.selectList(new LambdaQueryWrapper<DormRoom>().in(DormRoom::getFloorId, floorIds))
                        .forEach(r -> roomIds.add(r.getId()));
            }
        } else if ("FLOOR".equals(scopeType)) {
            dormRoomMapper.selectList(new LambdaQueryWrapper<DormRoom>().eq(DormRoom::getFloorId, floorId))
                    .forEach(r -> roomIds.add(r.getId()));
        }

        if (roomIds.isEmpty()) return Set.of();
        Set<Long> recipients = new HashSet<>();
        resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, batchId)
                        .in(AssignmentResult::getRoomId, roomIds))
                .forEach(r -> recipients.add(r.getStudentId()));
        return recipients;
    }

    private NoticeVO toVO(Notice n) {
        NoticeVO vo = new NoticeVO();
        vo.setId(n.getId());
        vo.setPublisherId(n.getPublisherId());
        if (n.getPublisherId() != null) {
            User u = userMapper.selectById(n.getPublisherId());
            vo.setPublisherName(u != null ? u.getRealName() : null);
        }
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setScopeType(n.getScopeType());
        vo.setBuildingId(n.getBuildingId());
        vo.setFloorId(n.getFloorId());
        vo.setStatus(n.getStatus());
        vo.setPublishTime(n.getPublishTime());
        vo.setCreateTime(n.getCreateTime());
        return vo;
    }
}

