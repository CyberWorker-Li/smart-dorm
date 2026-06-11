package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.entity.NotifyInbox;
import com.smartdorm.backend.mapper.NotifyInboxMapper;
import com.smartdorm.backend.vo.NotifyInboxVO;
import com.smartdorm.backend.vo.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotifyInboxService {

    private final NotifyInboxMapper notifyInboxMapper;

    public List<NotifyInboxVO> listInbox(Long userId, Boolean unreadOnly, String bizType, Integer limit) {
        int lim = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        LambdaQueryWrapper<NotifyInbox> w = new LambdaQueryWrapper<NotifyInbox>()
                .eq(NotifyInbox::getRecipientUserId, userId)
                .orderByDesc(NotifyInbox::getId)
                .last("LIMIT " + lim);

        if (Boolean.TRUE.equals(unreadOnly)) {
            w.isNull(NotifyInbox::getReadTime);
        }
        if (bizType != null && !bizType.isBlank()) {
            w.eq(NotifyInbox::getBizType, bizType.trim());
        }

        return notifyInboxMapper.selectList(w).stream().map(this::toVO).toList();
    }

    public UnreadCountVO unreadCount(Long userId) {
        Long c = notifyInboxMapper.selectCount(new LambdaQueryWrapper<NotifyInbox>()
                .eq(NotifyInbox::getRecipientUserId, userId)
                .isNull(NotifyInbox::getReadTime));
        UnreadCountVO vo = new UnreadCountVO();
        vo.setTotal(c == null ? 0 : c);
        return vo;
    }

    @Transactional
    public void markRead(Long inboxId, Long userId) {
        NotifyInbox row = notifyInboxMapper.selectById(inboxId);
        if (row == null) {
            throw new BusinessException("消息不存在");
        }
        if (!userId.equals(row.getRecipientUserId())) {
            throw new BusinessException("无权操作该消息");
        }
        if (row.getReadTime() != null) {
            return;
        }
        row.setReadTime(LocalDateTime.now());
        notifyInboxMapper.updateById(row);
    }

    @Transactional
    public void pushToUsers(Set<Long> userIds, String bizType, Long bizId, String title, String summary) {
        if (userIds == null || userIds.isEmpty()) return;
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new BusinessException("消息标题不能为空");

        String s = summary == null ? null : summary.trim();
        if (s != null && s.length() > 255) {
            s = s.substring(0, 255);
        }

        Set<Long> uniq = new HashSet<>(userIds);
        List<NotifyInbox> rows = new ArrayList<>(uniq.size());
        for (Long uid : uniq) {
            if (uid == null) continue;
            NotifyInbox row = new NotifyInbox();
            row.setRecipientUserId(uid);
            row.setBizType(bizType);
            row.setBizId(bizId);
            row.setTitle(t);
            row.setSummary(s);
            rows.add(row);
        }
        for (NotifyInbox r : rows) {
            notifyInboxMapper.insert(r);
        }
    }

    private NotifyInboxVO toVO(NotifyInbox row) {
        NotifyInboxVO vo = new NotifyInboxVO();
        vo.setInboxId(row.getId());
        vo.setBizType(row.getBizType());
        vo.setBizId(row.getBizId());
        vo.setTitle(row.getTitle());
        vo.setSummary(row.getSummary());
        vo.setReadTime(row.getReadTime());
        vo.setTime(row.getCreateTime());
        return vo;
    }
}

