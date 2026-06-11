package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.dto.CreateDormFeedbackRequest;
import com.smartdorm.backend.dto.CreateDormRuleRequest;
import com.smartdorm.backend.dto.CreateRepairRequest;
import com.smartdorm.backend.dto.SaveUtilityThresholdRequest;
import com.smartdorm.backend.dto.SubmitPeerEvalRequest;
import com.smartdorm.backend.dto.VoteDormRuleRequest;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InternalManageService {

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-\\d{2}$");

    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final UserMapper userMapper;
    private final DormRoomMapper dormRoomMapper;
    private final DormFloorMapper dormFloorMapper;
    private final DormManagerScopeMapper dormManagerScopeMapper;
    private final DormRoomService dormRoomService;

    private final NotifyInboxService notifyInboxService;

    private final DormRuleProposalMapper dormRuleProposalMapper;
    private final DormRuleVoteMapper dormRuleVoteMapper;
    private final DormFeedbackMapper dormFeedbackMapper;
    private final DormPeerEvalMapper dormPeerEvalMapper;
    private final DormPeerEvalItemMapper dormPeerEvalItemMapper;
    private final DormRepairRequestMapper dormRepairRequestMapper;
    private final DormUtilityMonthlyMapper dormUtilityMonthlyMapper;
    private final DormUtilityThresholdMapper dormUtilityThresholdMapper;
    private final DormActivityMapper dormActivityMapper;
    private final DormActivitySignupMapper dormActivitySignupMapper;

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
                .eq(AssignmentResult::getStudentId, studentId));
        if (r == null) throw new BusinessException("未找到已公示的分配结果，无法确定宿舍");
        return r.getRoomId();
    }

    private boolean isStudentInRoom(Long batchId, Long roomId, Long studentId) {
        Long c = resultMapper.selectCount(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getRoomId, roomId)
                .eq(AssignmentResult::getStudentId, studentId));
        return c != null && c > 0;
    }

    private int roomMemberCount(Long batchId, Long roomId) {
        Long c = resultMapper.selectCount(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getRoomId, roomId));
        return c == null ? 0 : c.intValue();
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

    private String roomDisplay(Long roomId) {
        DormRoom room = dormRoomService.getOrThrow(roomId);
        DormRoomVO vo = dormRoomService.toVO(room);
        String buildingName = vo.getBuildingName() == null ? "" : vo.getBuildingName();
        return (buildingName + " " + dormRoomService.formatRoomDisplay(room)).trim();
    }

    private String truncate255(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > 255) return t.substring(0, 255);
        return t;
    }

    @Transactional
    public Long createRule(CreateDormRuleRequest req) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, req.getStudentId());

        String c = req.getContent() == null ? "" : req.getContent().trim();
        if (c.isEmpty()) throw new BusinessException("公约内容不能为空");
        if (c.length() > 255) throw new BusinessException("公约内容过长");

        DormRuleProposal row = new DormRuleProposal();
        row.setBatchId(bid);
        row.setRoomId(roomId);
        row.setProposerStudentId(req.getStudentId());
        row.setContent(c);
        row.setStatus("VOTING");
        dormRuleProposalMapper.insert(row);
        return row.getId();
    }

    public List<RoommateVO> listMembers(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);

        List<AssignmentResult> members = resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, bid)
                .eq(AssignmentResult::getRoomId, roomId)
                .orderByAsc(AssignmentResult::getBedNo)
                .orderByAsc(AssignmentResult::getId));

        List<RoommateVO> out = new ArrayList<>();
        for (AssignmentResult r : members) {
            RoommateVO vo = new RoommateVO();
            vo.setStudentId(r.getStudentId());
            vo.setBedNo(r.getBedNo());
            User u = userMapper.selectById(r.getStudentId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setUserNo(u.getUserNo());
                vo.setGender(u.getGender());
            }
            out.add(vo);
        }
        return out;
    }

    public List<DormRuleVO> listRules(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);
        int memberCount = Math.max(1, roomMemberCount(bid, roomId));

        List<DormRuleProposal> proposals = dormRuleProposalMapper.selectList(new LambdaQueryWrapper<DormRuleProposal>()
                .eq(DormRuleProposal::getBatchId, bid)
                .eq(DormRuleProposal::getRoomId, roomId)
                .orderByDesc(DormRuleProposal::getId)
                .last("LIMIT 50"));

        List<DormRuleVO> out = new ArrayList<>();
        for (DormRuleProposal p : proposals) {
            Long agree = dormRuleVoteMapper.selectCount(new LambdaQueryWrapper<DormRuleVote>()
                    .eq(DormRuleVote::getProposalId, p.getId())
                    .eq(DormRuleVote::getAgree, true));
            int agreeCount = agree == null ? 0 : agree.intValue();
            int rate = (int) Math.round((agreeCount * 100.0) / memberCount);

            Long voted = dormRuleVoteMapper.selectCount(new LambdaQueryWrapper<DormRuleVote>()
                    .eq(DormRuleVote::getProposalId, p.getId())
                    .eq(DormRuleVote::getVoterStudentId, studentId));

            Long totalVotes = dormRuleVoteMapper.selectCount(new LambdaQueryWrapper<DormRuleVote>()
                    .eq(DormRuleVote::getProposalId, p.getId()));
            int total = totalVotes == null ? 0 : totalVotes.intValue();

            String status = p.getStatus();
            if (rate >= 60) status = "EFFECTIVE";
            else if (total >= memberCount) status = "REJECTED";
            else status = "VOTING";

            if (!Objects.equals(status, p.getStatus())) {
                DormRuleProposal upd = new DormRuleProposal();
                upd.setId(p.getId());
                upd.setStatus(status);
                dormRuleProposalMapper.updateById(upd);
            }

            DormRuleVO vo = new DormRuleVO();
            vo.setId(p.getId());
            vo.setContent(p.getContent());
            vo.setStatus(status);
            vo.setApprovalRate(rate);
            vo.setVoted(voted != null && voted > 0);
            vo.setCreateTime(p.getCreateTime());
            out.add(vo);
        }
        return out;
    }

    @Transactional
    public boolean voteRule(Long proposalId, VoteDormRuleRequest req) {
        DormRuleProposal p = dormRuleProposalMapper.selectById(proposalId);
        if (p == null) throw new BusinessException("公约不存在");

        Long bid = p.getBatchId();
        if (!isStudentInRoom(bid, p.getRoomId(), req.getStudentId())) {
            throw new BusinessException("无权投票该公约");
        }
        if (!"VOTING".equals(p.getStatus())) throw new BusinessException("该公约已结束投票");

        DormRuleVote v = new DormRuleVote();
        v.setProposalId(proposalId);
        v.setVoterStudentId(req.getStudentId());
        v.setAgree(Boolean.TRUE.equals(req.getAgree()));
        try {
            dormRuleVoteMapper.insert(v);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("您已投票");
        }

        int memberCount = Math.max(1, roomMemberCount(bid, p.getRoomId()));
        Long agree = dormRuleVoteMapper.selectCount(new LambdaQueryWrapper<DormRuleVote>()
                .eq(DormRuleVote::getProposalId, proposalId)
                .eq(DormRuleVote::getAgree, true));
        int agreeCount = agree == null ? 0 : agree.intValue();
        int rate = (int) Math.round((agreeCount * 100.0) / memberCount);

        Long totalVotes = dormRuleVoteMapper.selectCount(new LambdaQueryWrapper<DormRuleVote>()
                .eq(DormRuleVote::getProposalId, proposalId));
        int total = totalVotes == null ? 0 : totalVotes.intValue();

        String status;
        if (rate >= 60) status = "EFFECTIVE";
        else if (total >= memberCount) status = "REJECTED";
        else status = "VOTING";

        DormRuleProposal upd = new DormRuleProposal();
        upd.setId(proposalId);
        upd.setStatus(status);
        dormRuleProposalMapper.updateById(upd);
        return true;
    }

    @Transactional
    public Long createFeedback(CreateDormFeedbackRequest req) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, req.getStudentId());

        String c = req.getContent() == null ? "" : req.getContent().trim();
        if (c.isEmpty()) throw new BusinessException("反馈内容不能为空");
        if (c.length() > 500) throw new BusinessException("反馈内容过长");

        DormFeedback row = new DormFeedback();
        row.setBatchId(bid);
        row.setRoomId(roomId);
        row.setSubmitterStudentId(req.getStudentId());
        row.setContent(c);
        row.setStatus("SUBMITTED");
        dormFeedbackMapper.insert(row);
        return row.getId();
    }

    public List<DormFeedbackVO> listFeedback(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);
        List<DormFeedback> rows = dormFeedbackMapper.selectList(new LambdaQueryWrapper<DormFeedback>()
                .eq(DormFeedback::getBatchId, bid)
                .eq(DormFeedback::getRoomId, roomId)
                .orderByDesc(DormFeedback::getId)
                .last("LIMIT 50"));

        List<DormFeedbackVO> out = new ArrayList<>();
        for (DormFeedback r : rows) {
            DormFeedbackVO vo = new DormFeedbackVO();
            vo.setId(r.getId());
            vo.setContent(r.getContent());
            vo.setStatus(r.getStatus());
            vo.setTime(r.getCreateTime());
            vo.setCanEscalate("SUBMITTED".equals(r.getStatus()));
            out.add(vo);
        }
        return out;
    }

    @Transactional
    public boolean escalateFeedback(Long feedbackId, Long studentId) {
        DormFeedback row = dormFeedbackMapper.selectById(feedbackId);
        if (row == null) throw new BusinessException("反馈不存在");

        if (!isStudentInRoom(row.getBatchId(), row.getRoomId(), studentId)) {
            throw new BusinessException("无权操作该反馈");
        }
        if ("ESCALATED".equals(row.getStatus()) || "RESOLVED".equals(row.getStatus())) return true;

        row.setStatus("ESCALATED");
        row.setEscalateTime(LocalDateTime.now());
        dormFeedbackMapper.updateById(row);

        Set<Long> managers = managersForRoom(row.getRoomId());
        notifyInboxService.pushToUsers(managers, "DORM_FEEDBACK", row.getId(), "宿舍内部意见反馈", truncate255(row.getContent()));
        return true;
    }

    public List<PeerEvalTemplateItemVO> peerEvalTemplate(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);

        List<AssignmentResult> members = resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, bid)
                .eq(AssignmentResult::getRoomId, roomId)
                .orderByAsc(AssignmentResult::getBedNo)
                .orderByAsc(AssignmentResult::getId));

        List<PeerEvalTemplateItemVO> out = new ArrayList<>();
        for (AssignmentResult r : members) {
            if (studentId.equals(r.getStudentId())) continue;
            User u = userMapper.selectById(r.getStudentId());
            PeerEvalTemplateItemVO vo = new PeerEvalTemplateItemVO();
            vo.setTargetStudentId(r.getStudentId());
            vo.setName(u != null && u.getRealName() != null ? u.getRealName() : String.valueOf(r.getStudentId()));
            vo.setSchedule(8);
            vo.setHygiene(8);
            vo.setCommunication(8);
            out.add(vo);
        }
        return out;
    }

    @Transactional
    public boolean submitPeerEval(SubmitPeerEvalRequest req) {
        if (!MONTH.matcher(req.getMonth().trim()).matches()) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }

        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, req.getStudentId());

        Set<Long> validTargets = resultMapper.selectList(new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, bid)
                        .eq(AssignmentResult::getRoomId, roomId))
                .stream().map(AssignmentResult::getStudentId).collect(java.util.stream.Collectors.toSet());
        validTargets.remove(req.getStudentId());

        List<SubmitPeerEvalRequest.Item> items = req.getItems() == null ? List.of() : req.getItems();
        if (items.isEmpty()) throw new BusinessException("互评明细不能为空");

        Set<Long> seen = new HashSet<>();
        for (SubmitPeerEvalRequest.Item i : items) {
            if (i.getTargetStudentId() == null) throw new BusinessException("被评学生ID不能为空");
            if (!validTargets.contains(i.getTargetStudentId())) throw new BusinessException("互评对象非法");
            if (!seen.add(i.getTargetStudentId())) throw new BusinessException("互评对象重复");
        }

        DormPeerEval header = dormPeerEvalMapper.selectOne(new LambdaQueryWrapper<DormPeerEval>()
                .eq(DormPeerEval::getBatchId, bid)
                .eq(DormPeerEval::getRoomId, roomId)
                .eq(DormPeerEval::getSubmitterStudentId, req.getStudentId())
                .eq(DormPeerEval::getMonth, req.getMonth().trim())
                .last("LIMIT 1"));

        if (header == null) {
            header = new DormPeerEval();
            header.setBatchId(bid);
            header.setRoomId(roomId);
            header.setSubmitterStudentId(req.getStudentId());
            header.setMonth(req.getMonth().trim());
            header.setStatus("SUBMITTED");
            dormPeerEvalMapper.insert(header);
        } else {
            dormPeerEvalItemMapper.delete(new LambdaQueryWrapper<DormPeerEvalItem>()
                    .eq(DormPeerEvalItem::getEvalId, header.getId()));
        }

        boolean lowScore = false;
        for (SubmitPeerEvalRequest.Item i : items) {
            DormPeerEvalItem row = new DormPeerEvalItem();
            row.setEvalId(header.getId());
            row.setTargetStudentId(i.getTargetStudentId());
            row.setScheduleScore(i.getSchedule());
            row.setHygieneScore(i.getHygiene());
            row.setCommunicationScore(i.getCommunication());
            dormPeerEvalItemMapper.insert(row);

            double avg = (i.getSchedule() + i.getHygiene() + i.getCommunication()) / 3.0;
            if (avg <= 4.0) {
                lowScore = true;
            }
        }

        if (lowScore) {
            Set<Long> managers = managersForRoom(roomId);
            notifyInboxService.pushToUsers(managers, "PEER_EVAL_WARNING", header.getId(), "室友互评预警", "存在低分互评，请关注该宿舍冲突风险");
        }
        return true;
    }

    public List<PeerEvalManagerListItemVO> listPeerEvalForManager(Long managerId, String month, Long roomId, Integer limit) {
        if (managerId == null) throw new BusinessException("宿管ID不能为空");
        int lim = limit == null ? 50 : Math.max(1, Math.min(limit, 200));

        List<DormRoomVO> myRooms = dormRoomService.listRoomsByManager(managerId);
        if (myRooms.isEmpty()) return List.of();
        Set<Long> roomIds = new HashSet<>(myRooms.stream().map(DormRoomVO::getId).toList());
        if (roomId != null && !roomIds.contains(roomId)) {
            throw new BusinessException("无权查看该房间互评");
        }

        String m = month == null ? null : month.trim();
        if (m != null && !m.isEmpty() && !MONTH.matcher(m).matches()) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }

        LambdaQueryWrapper<DormPeerEval> w = new LambdaQueryWrapper<DormPeerEval>()
                .in(DormPeerEval::getRoomId, roomId != null ? List.of(roomId) : new ArrayList<>(roomIds))
                .orderByDesc(DormPeerEval::getId)
                .last("LIMIT " + lim);
        if (m != null && !m.isEmpty()) {
            w.eq(DormPeerEval::getMonth, m);
        }

        List<DormPeerEval> rows = dormPeerEvalMapper.selectList(w);
        List<PeerEvalManagerListItemVO> out = new ArrayList<>();
        for (DormPeerEval e : rows) {
            List<DormPeerEvalItem> items = dormPeerEvalItemMapper.selectList(new LambdaQueryWrapper<DormPeerEvalItem>()
                    .eq(DormPeerEvalItem::getEvalId, e.getId())
                    .orderByAsc(DormPeerEvalItem::getId));

            int lowCount = 0;
            for (DormPeerEvalItem it : items) {
                double avg = (it.getScheduleScore() + it.getHygieneScore() + it.getCommunicationScore()) / 3.0;
                if (avg <= 4.0) lowCount++;
            }

            PeerEvalManagerListItemVO vo = new PeerEvalManagerListItemVO();
            vo.setEvalId(e.getId());
            vo.setBatchId(e.getBatchId());
            vo.setRoomId(e.getRoomId());
            vo.setRoomDisplay(roomDisplay(e.getRoomId()));
            vo.setMonth(e.getMonth());
            vo.setSubmitterStudentId(e.getSubmitterStudentId());
            User submitter = userMapper.selectById(e.getSubmitterStudentId());
            vo.setSubmitterName(submitter != null ? submitter.getRealName() : null);
            vo.setCreateTime(e.getCreateTime());
            vo.setLowRisk(lowCount > 0);
            vo.setLowItemCount(lowCount);
            out.add(vo);
        }
        return out;
    }

    public PeerEvalManagerDetailVO peerEvalDetailForManager(Long managerId, Long evalId) {
        if (managerId == null) throw new BusinessException("宿管ID不能为空");
        if (evalId == null) throw new BusinessException("互评ID不能为空");

        DormPeerEval e = dormPeerEvalMapper.selectById(evalId);
        if (e == null) throw new BusinessException("互评不存在");

        List<DormRoomVO> myRooms = dormRoomService.listRoomsByManager(managerId);
        Set<Long> roomIds = new HashSet<>(myRooms.stream().map(DormRoomVO::getId).toList());
        if (!roomIds.contains(e.getRoomId())) {
            throw new BusinessException("无权查看该互评");
        }

        List<DormPeerEvalItem> items = dormPeerEvalItemMapper.selectList(new LambdaQueryWrapper<DormPeerEvalItem>()
                .eq(DormPeerEvalItem::getEvalId, e.getId())
                .orderByAsc(DormPeerEvalItem::getId));

        List<PeerEvalManagerDetailItemVO> detailItems = new ArrayList<>();
        for (DormPeerEvalItem it : items) {
            PeerEvalManagerDetailItemVO vo = new PeerEvalManagerDetailItemVO();
            vo.setTargetStudentId(it.getTargetStudentId());
            User u = userMapper.selectById(it.getTargetStudentId());
            vo.setTargetName(u != null ? u.getRealName() : null);
            vo.setScheduleScore(it.getScheduleScore());
            vo.setHygieneScore(it.getHygieneScore());
            vo.setCommunicationScore(it.getCommunicationScore());
            double avg = (it.getScheduleScore() + it.getHygieneScore() + it.getCommunicationScore()) / 3.0;
            vo.setAvgScore(avg);
            vo.setLow(avg <= 4.0);
            detailItems.add(vo);
        }

        PeerEvalManagerDetailVO vo = new PeerEvalManagerDetailVO();
        vo.setEvalId(e.getId());
        vo.setBatchId(e.getBatchId());
        vo.setRoomId(e.getRoomId());
        vo.setRoomDisplay(roomDisplay(e.getRoomId()));
        vo.setMonth(e.getMonth());
        vo.setSubmitterStudentId(e.getSubmitterStudentId());
        User submitter = userMapper.selectById(e.getSubmitterStudentId());
        vo.setSubmitterName(submitter != null ? submitter.getRealName() : null);
        vo.setCreateTime(e.getCreateTime());
        vo.setItems(detailItems);
        return vo;
    }

    @Transactional
    public RepairSubmitVO submitRepair(CreateRepairRequest req) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, req.getStudentId());

        String type = req.getType() == null ? "" : req.getType().trim().toUpperCase();
        if (!"WATER".equals(type) && !"POWER".equals(type) && !"MECHANICAL".equals(type)) {
            throw new BusinessException("故障类型非法");
        }
        String detail = req.getDetail() == null ? "" : req.getDetail().trim();
        if (detail.length() > 255) throw new BusinessException("故障描述过长");

        String reply;
        if ("WATER".equals(type)) reply = "初步建议：先检查阀门/水龙头是否完全关闭；如仍漏水请联系水电维修：138xxxx0000";
        else if ("POWER".equals(type)) reply = "初步建议：先确认是否跳闸/漏保断开；如无法恢复请联系电工维修：139xxxx0000";
        else reply = "初步建议：先确认螺丝/卡扣是否松动；如有安全隐患请联系设施维修：137xxxx0000";

        DormRepairRequest row = new DormRepairRequest();
        row.setBatchId(bid);
        row.setRoomId(roomId);
        row.setSubmitterStudentId(req.getStudentId());
        row.setType(type);
        row.setDetail(detail);
        row.setReply(reply);
        row.setStatus("SUBMITTED");
        dormRepairRequestMapper.insert(row);

        Set<Long> managers = managersForRoom(roomId);
        notifyInboxService.pushToUsers(managers, "REPAIR", row.getId(), "宿舍报修", truncate255(detail.isEmpty() ? type : detail));

        RepairSubmitVO vo = new RepairSubmitVO();
        vo.setId(row.getId());
        vo.setReply(reply);
        return vo;
    }

    public List<UtilityVO> listUtilities(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);
        List<DormUtilityMonthly> rows = dormUtilityMonthlyMapper.selectList(new LambdaQueryWrapper<DormUtilityMonthly>()
                .eq(DormUtilityMonthly::getRoomId, roomId)
                .orderByDesc(DormUtilityMonthly::getMonth)
                .last("LIMIT 12"));
        return rows.stream().map(r -> {
            UtilityVO vo = new UtilityVO();
            vo.setMonth(r.getMonth());
            vo.setWater(r.getWater());
            vo.setPower(r.getPower());
            vo.setCost(r.getCost());
            return vo;
        }).toList();
    }

    public UtilityThresholdVO getUtilityThreshold(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);
        DormUtilityThreshold row = dormUtilityThresholdMapper.selectOne(new LambdaQueryWrapper<DormUtilityThreshold>()
                .eq(DormUtilityThreshold::getRoomId, roomId)
                .last("LIMIT 1"));
        UtilityThresholdVO vo = new UtilityThresholdVO();
        vo.setWaterLimit(row == null ? null : row.getWaterLimit());
        vo.setPowerLimit(row == null ? null : row.getPowerLimit());
        return vo;
    }

    @Transactional
    public boolean saveUtilityThreshold(SaveUtilityThresholdRequest req) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, req.getStudentId());

        DormUtilityThreshold row = dormUtilityThresholdMapper.selectOne(new LambdaQueryWrapper<DormUtilityThreshold>()
                .eq(DormUtilityThreshold::getRoomId, roomId)
                .last("LIMIT 1"));

        if (row == null) {
            row = new DormUtilityThreshold();
            row.setRoomId(roomId);
            row.setWaterLimit(req.getWaterLimit());
            row.setPowerLimit(req.getPowerLimit());
            dormUtilityThresholdMapper.insert(row);
        } else {
            row.setWaterLimit(req.getWaterLimit());
            row.setPowerLimit(req.getPowerLimit());
            dormUtilityThresholdMapper.updateById(row);
        }
        return true;
    }

    public List<ActivityVO> listActivities(Long studentId) {
        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);

        List<DormActivity> rows = dormActivityMapper.selectList(new LambdaQueryWrapper<DormActivity>()
                .eq(DormActivity::getStatus, "OPEN")
                .orderByDesc(DormActivity::getId)
                .last("LIMIT 50"));

        List<ActivityVO> out = new ArrayList<>();
        for (DormActivity a : rows) {
            Long c = dormActivitySignupMapper.selectCount(new LambdaQueryWrapper<DormActivitySignup>()
                    .eq(DormActivitySignup::getActivityId, a.getId())
                    .eq(DormActivitySignup::getRoomId, roomId));
            ActivityVO vo = new ActivityVO();
            vo.setId(a.getId());
            vo.setName(a.getName());
            vo.setStatus(a.getStatus());
            vo.setJoined(c != null && c > 0);
            out.add(vo);
        }
        return out;
    }

    @Transactional
    public boolean signupActivity(Long activityId, Long studentId) {
        DormActivity a = dormActivityMapper.selectById(activityId);
        if (a == null) throw new BusinessException("活动不存在");
        if (!"OPEN".equals(a.getStatus())) throw new BusinessException("活动已结束或不可报名");

        Long bid = latestPublishedBatchId();
        Long roomId = roomIdOfStudent(bid, studentId);

        DormActivitySignup row = new DormActivitySignup();
        row.setActivityId(activityId);
        row.setRoomId(roomId);
        row.setSignupStudentId(studentId);
        try {
            dormActivitySignupMapper.insert(row);
        } catch (DuplicateKeyException e) {
            return true;
        }
        return true;
    }
}
