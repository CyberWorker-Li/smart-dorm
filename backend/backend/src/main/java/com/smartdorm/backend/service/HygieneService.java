package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.dto.*;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HygieneService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HygieneDutyTaskMapper taskMapper;
    private final HygieneCheckinMapper checkinMapper;
    private final HygieneScoreMapper scoreMapper;
    private final HygieneRuleMapper ruleMapper;
    private final HygieneRewardPunishmentMapper rpMapper;

    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final DormRoomService dormRoomService;
    private final UserMapper userMapper;
    private final SysUserStudentMapper sysUserStudentMapper;

    public List<HygieneTaskVO> listMyTasks(Long studentId, LocalDate from, LocalDate to) {
        Long roomId = getCurrentRoomIdOrThrow(studentId);
        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .eq(HygieneDutyTask::getDutyUserId, studentId)
                        .ge(from != null, HygieneDutyTask::getDutyDate, from)
                        .le(to != null, HygieneDutyTask::getDutyDate, to)
                        .orderByAsc(HygieneDutyTask::getDutyDate)
                        .orderByAsc(HygieneDutyTask::getId));
        return toTaskVOList(roomId, tasks);
    }

    public List<HygieneTaskVO> listRoomTasks(Long studentId, LocalDate from, LocalDate to) {
        Long roomId = getCurrentRoomIdOrThrow(studentId);
        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .ge(from != null, HygieneDutyTask::getDutyDate, from)
                        .le(to != null, HygieneDutyTask::getDutyDate, to)
                        .orderByAsc(HygieneDutyTask::getDutyDate)
                        .orderByAsc(HygieneDutyTask::getId));
        return toTaskVOList(roomId, tasks);
    }

    public List<HygieneMemberVO> leaderMembers(Long leaderId) {
        requireLeader(leaderId);
        List<Long> memberIds = listRoomMemberIdsOrThrow(leaderId).stream().sorted().toList();
        if (memberIds.isEmpty()) return List.of();
        Map<Long, String> userNameById = userMapper.selectBatchIds(memberIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getRealName() == null ? u.getUsername() : u.getRealName(), (a, b) -> a));
        List<HygieneMemberVO> vos = new ArrayList<>();
        for (Long id : memberIds) {
            HygieneMemberVO vo = new HygieneMemberVO();
            vo.setId(id);
            vo.setName(userNameById.getOrDefault(id, String.valueOf(id)));
            vos.add(vo);
        }
        return vos;
    }

    @Transactional
    public int deleteWeek(HygieneLeaderDeleteWeekRequest request) {
        requireLeader(request.getLeaderId());
        Long roomId = getCurrentRoomIdOrThrow(request.getLeaderId());
        LocalDate weekStart = parseDateOrThrow(request.getWeekStart());
        LocalDate weekEnd = weekStart.plusDays(6);

        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .ge(HygieneDutyTask::getDutyDate, weekStart)
                        .le(HygieneDutyTask::getDutyDate, weekEnd));
        if (tasks.isEmpty()) return 0;

        Set<Long> taskIds = tasks.stream().map(HygieneDutyTask::getId).collect(Collectors.toSet());
        Long checkins = checkinMapper.selectCount(new LambdaQueryWrapper<HygieneCheckin>().in(HygieneCheckin::getTaskId, taskIds));
        if (checkins != null && checkins > 0) {
            throw new BusinessException("本周已有打卡记录，无法删除值日表");
        }
        boolean hasCompleted = tasks.stream().anyMatch(t -> "COMPLETED".equals(t.getStatus()));
        if (hasCompleted) {
            throw new BusinessException("本周存在已完成任务，无法删除值日表");
        }

        return taskMapper.delete(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .ge(HygieneDutyTask::getDutyDate, weekStart)
                        .le(HygieneDutyTask::getDutyDate, weekEnd));
    }

    @Transactional
    public int generateWeek(HygieneGenerateWeekRequest request) {
        if (request.getLeaderId() == null) {
            throw new BusinessException("leaderId 不能为空");
        }
        requireLeader(request.getLeaderId());
        Long roomId = getCurrentRoomIdOrThrow(request.getLeaderId());
        LocalDate weekStart = parseDateOrThrow(request.getWeekStart());

        List<Long> memberIds = listRoomMemberIdsOrThrow(request.getLeaderId());
        memberIds = memberIds.stream().sorted().toList();

        LocalDate weekEnd = weekStart.plusDays(6);
        Long existing = taskMapper.selectCount(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .ge(HygieneDutyTask::getDutyDate, weekStart)
                        .le(HygieneDutyTask::getDutyDate, weekEnd));
        if (existing != null && existing > 0) {
            return 0;
        }

        String dutyItem = request.getDutyItem() == null || request.getDutyItem().isBlank()
                ? "宿舍公共区域卫生"
                : request.getDutyItem().trim();

        int deadlineHour = getRuleInt("hygiene.deadlineHour", 22);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Long uid = memberIds.get(i % memberIds.size());
            HygieneDutyTask task = new HygieneDutyTask();
            task.setRoomId(roomId);
            task.setDutyDate(date);
            task.setDutyUserId(uid);
            task.setDutyItem(dutyItem);
            task.setStatus("PENDING");
            task.setDeadlineTime(date.atTime(deadlineHour, 0));
            task.setCreatedBy(request.getLeaderId());
            taskMapper.insert(task);
        }
        return 7;
    }

    @Transactional
    public Long upsertTask(HygieneLeaderUpsertTaskRequest request) {
        requireLeader(request.getLeaderId());
        Long roomId = getCurrentRoomIdOrThrow(request.getLeaderId());

        LocalDate dutyDate = parseDateOrThrow(request.getDutyDate());
        String dutyItem = request.getDutyItem() == null || request.getDutyItem().isBlank()
                ? "宿舍公共区域卫生"
                : request.getDutyItem().trim();

        List<Long> memberIds = listRoomMemberIdsOrThrow(request.getLeaderId());
        if (!memberIds.contains(request.getDutyUserId())) throw new BusinessException("只能指派本宿舍成员");

        HygieneDutyTask existing = taskMapper.selectOne(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .eq(HygieneDutyTask::getDutyDate, dutyDate)
                        .last("limit 1"));

        LocalDateTime deadlineTime = parseDeadlineOrDefault(dutyDate, request.getDeadlineTime());
        if (existing == null) {
            HygieneDutyTask task = new HygieneDutyTask();
            task.setRoomId(roomId);
            task.setDutyDate(dutyDate);
            task.setDutyUserId(request.getDutyUserId());
            task.setDutyItem(dutyItem);
            task.setStatus("PENDING");
            task.setDeadlineTime(deadlineTime);
            task.setCreatedBy(request.getLeaderId());
            taskMapper.insert(task);
            return task.getId();
        }

        if (Objects.equals(existing.getStatus(), "COMPLETED")) {
            throw new BusinessException("任务已完成，无法修改");
        }
        Long checkinCount = checkinMapper.selectCount(new LambdaQueryWrapper<HygieneCheckin>().eq(HygieneCheckin::getTaskId, existing.getId()));
        if (checkinCount != null && checkinCount > 0) {
            throw new BusinessException("任务已产生打卡记录，无法修改");
        }

        existing.setDutyUserId(request.getDutyUserId());
        existing.setDutyItem(dutyItem);
        existing.setDeadlineTime(deadlineTime);
        existing.setStatus("PENDING");
        taskMapper.updateById(existing);
        return existing.getId();
    }

    @Transactional
    public void reassign(HygieneReassignRequest request) {
        requireLeader(request.getLeaderId());
        HygieneDutyTask task = taskMapper.selectById(request.getTaskId());
        if (task == null) throw new BusinessException("任务不存在");
        Long roomId = getCurrentRoomIdOrThrow(request.getLeaderId());
        if (!roomId.equals(task.getRoomId())) throw new BusinessException("只能调整本宿舍任务");
        if (Objects.equals(task.getStatus(), "COMPLETED")) throw new BusinessException("任务已完成，无法调整");

        List<Long> memberIds = listRoomMemberIdsOrThrow(request.getLeaderId());
        if (!memberIds.contains(request.getNewUserId())) throw new BusinessException("只能改派给本宿舍成员");
        task.setDutyUserId(request.getNewUserId());
        taskMapper.updateById(task);
    }

    @Transactional
    public Long checkin(HygieneCheckinRequest request) {
        HygieneDutyTask task = taskMapper.selectById(request.getTaskId());
        if (task == null) throw new BusinessException("任务不存在");
        Long roomId = getCurrentRoomIdOrThrow(request.getStudentId());
        if (!roomId.equals(task.getRoomId())) throw new BusinessException("只能打卡本宿舍任务");
        if (!Objects.equals(task.getDutyUserId(), request.getStudentId())) {
            throw new BusinessException("只能为自己当天值日任务打卡");
        }
        if ("COMPLETED".equals(task.getStatus())) {
            HygieneCheckin existing = checkinMapper.selectOne(
                    new LambdaQueryWrapper<HygieneCheckin>()
                            .eq(HygieneCheckin::getTaskId, task.getId())
                            .orderByDesc(HygieneCheckin::getId)
                            .last("limit 1"));
            return existing != null ? existing.getId() : null;
        }

        HygieneCheckin checkin = new HygieneCheckin();
        checkin.setTaskId(task.getId());
        checkin.setUserId(request.getStudentId());
        checkin.setPhotoUrl(request.getPhotoUrl().trim());
        checkin.setLocationText(request.getLocationText());
        checkin.setRemark(request.getRemark());
        checkin.setCheckinTime(LocalDateTime.now());
        checkin.setVerifyStatus("PENDING");
        checkinMapper.insert(checkin);

        task.setStatus("COMPLETED");
        taskMapper.updateById(task);
        return checkin.getId();
    }

    public HygieneRoomSummaryVO myRoomSummary(Long studentId) {
        Long roomId = getCurrentRoomIdOrThrow(studentId);
        String roomDisplay = dormRoomService.formatRoomDisplay(dormRoomService.getOrThrow(roomId));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(30);
        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .ge(HygieneDutyTask::getDutyDate, from)
                        .le(HygieneDutyTask::getDutyDate, today));
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        int overdue = (int) tasks.stream().filter(t -> "OVERDUE".equals(t.getStatus())).count();

        List<HygieneScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<HygieneScore>()
                        .eq(HygieneScore::getRoomId, roomId)
                        .orderByDesc(HygieneScore::getScoreDate)
                        .orderByDesc(HygieneScore::getId)
                        .last("limit 20"));
        Integer latest = scores.isEmpty() ? null : scores.get(0).getScore();
        Double avg = scores.isEmpty() ? null : scores.stream().map(HygieneScore::getScore).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        List<HygieneScoreItemVO> scoreVos = scores.stream().map(this::toScoreVO).toList();

        HygieneRoomSummaryVO vo = new HygieneRoomSummaryVO();
        vo.setRoomId(roomId);
        vo.setRoomDisplay(roomDisplay);
        vo.setTotalTasks(total);
        vo.setCompletedTasks(completed);
        vo.setOverdueTasks(overdue);
        vo.setLatestScore(latest);
        vo.setAvgScore(avg);
        vo.setRecentScores(scoreVos);
        return vo;
    }

    public List<HygieneTaskVO> managerRoomTasks(Long managerId, Long roomId, LocalDate from, LocalDate to) {
        requireDormManager(managerId, roomId);
        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .ge(from != null, HygieneDutyTask::getDutyDate, from)
                        .le(to != null, HygieneDutyTask::getDutyDate, to)
                        .orderByDesc(HygieneDutyTask::getDutyDate)
                        .orderByDesc(HygieneDutyTask::getId));
        return toTaskVOList(roomId, tasks);
    }

    public List<HygieneTaskVO> managerPendingCheckins(Long managerId, Long roomId) {
        requireDormManager(managerId, roomId);
        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getRoomId, roomId)
                        .orderByDesc(HygieneDutyTask::getDutyDate)
                        .orderByDesc(HygieneDutyTask::getId)
                        .last("limit 30"));
        List<HygieneTaskVO> vos = toTaskVOList(roomId, tasks);
        return vos.stream().filter(v -> v.isCheckedIn() && "PENDING".equals(v.getVerifyStatus())).toList();
    }

    @Transactional
    public void verifyCheckin(HygieneVerifyCheckinRequest request) {
        HygieneCheckin checkin = checkinMapper.selectById(request.getCheckinId());
        if (checkin == null) throw new BusinessException("打卡记录不存在");
        HygieneDutyTask task = taskMapper.selectById(checkin.getTaskId());
        if (task == null) throw new BusinessException("任务不存在");
        requireDormManager(request.getManagerId(), task.getRoomId());
        String status = request.getVerifyStatus().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException("verifyStatus 只能为 APPROVED 或 REJECTED");
        }
        checkin.setVerifyStatus(status);
        checkin.setVerifiedBy(request.getManagerId());
        checkin.setVerifiedTime(LocalDateTime.now());
        checkinMapper.updateById(checkin);

        HygieneCheckin latest = checkinMapper.selectOne(
                new LambdaQueryWrapper<HygieneCheckin>()
                        .eq(HygieneCheckin::getTaskId, task.getId())
                        .orderByDesc(HygieneCheckin::getId)
                        .last("limit 1"));
        if (latest != null && Objects.equals(latest.getId(), checkin.getId())) {
            if ("REJECTED".equals(status)) {
                task.setStatus("PENDING");
            } else if ("APPROVED".equals(status)) {
                task.setStatus("COMPLETED");
            }
            taskMapper.updateById(task);
        }
    }

    @Transactional
    public Long inspect(HygieneInspectRequest request) {
        requireDormManager(request.getManagerId(), request.getRoomId());
        LocalDate date = request.getScoreDate() == null || request.getScoreDate().isBlank()
                ? LocalDate.now()
                : parseDateOrThrow(request.getScoreDate());

        HygieneScore score = new HygieneScore();
        score.setRoomId(request.getRoomId());
        score.setScoreDate(date);
        score.setPeriodType("INSPECTION");
        score.setScore(request.getScore());
        score.setSourceType("DORM_MANAGER");
        score.setReason(request.getReason());
        score.setInspectorId(request.getManagerId());
        scoreMapper.insert(score);

        int red = getRuleInt("hygiene.redThreshold", 90);
        int black = getRuleInt("hygiene.blackThreshold", 70);
        if (request.getScore() >= red) {
            createRoomRp(request.getRoomId(), null, "REWARD", 1, "卫生评分优秀（宿管检查）", score.getId(), request.getManagerId());
        } else if (request.getScore() < black) {
            createRoomRp(request.getRoomId(), null, "PUNISH", -1, "卫生评分不达标（宿管检查）", score.getId(), request.getManagerId());
        }
        return score.getId();
    }

    public List<HygieneRuleVO> listRules() {
        List<HygieneRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<HygieneRule>().orderByAsc(HygieneRule::getRuleKey));
        return rules.stream().map(r -> {
            HygieneRuleVO vo = new HygieneRuleVO();
            vo.setKey(r.getRuleKey());
            vo.setValue(r.getRuleValue());
            vo.setRemark(r.getRemark());
            return vo;
        }).toList();
    }

    @Transactional
    public void upsertRules(HygieneUpsertRulesRequest request) {
        if (request.getAdminId() == null) throw new BusinessException("adminId 不能为空");
        for (HygieneRuleItem item : request.getItems()) {
            HygieneRule existing = ruleMapper.selectOne(
                    new LambdaQueryWrapper<HygieneRule>().eq(HygieneRule::getRuleKey, item.getKey()).last("limit 1"));
            if (existing == null) {
                HygieneRule rule = new HygieneRule();
                rule.setRuleKey(item.getKey().trim());
                rule.setRuleValue(item.getValue().trim());
                rule.setRemark(item.getRemark());
                ruleMapper.insert(rule);
            } else {
                existing.setRuleValue(item.getValue().trim());
                existing.setRemark(item.getRemark());
                ruleMapper.updateById(existing);
            }
        }
    }

    public List<HygieneRankingItemVO> monthRanking(String month) {
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month.trim());
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        double systemWeight = getRuleDouble("hygiene.systemWeight", 0.4);
        double manualWeight = getRuleDouble("hygiene.manualWeight", 0.6);
        int red = getRuleInt("hygiene.redThreshold", 90);
        int black = getRuleInt("hygiene.blackThreshold", 70);

        List<HygieneScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<HygieneScore>()
                        .ge(HygieneScore::getScoreDate, start)
                        .le(HygieneScore::getScoreDate, end));
        Map<Long, List<HygieneScore>> byRoom = scores.stream().collect(Collectors.groupingBy(HygieneScore::getRoomId));

        List<HygieneRankingItemVO> items = new ArrayList<>();
        for (Map.Entry<Long, List<HygieneScore>> e : byRoom.entrySet()) {
            Long roomId = e.getKey();
            List<HygieneScore> roomScores = e.getValue();
            List<Integer> system = roomScores.stream()
                    .filter(s -> "SYSTEM".equals(s.getSourceType()))
                    .map(HygieneScore::getScore).filter(Objects::nonNull).toList();
            List<Integer> manual = roomScores.stream()
                    .filter(s -> "DORM_MANAGER".equals(s.getSourceType()))
                    .map(HygieneScore::getScore).filter(Objects::nonNull).toList();

            double systemAvg = system.isEmpty() ? 0 : system.stream().mapToInt(Integer::intValue).average().orElse(0);
            double manualAvg = manual.isEmpty() ? 0 : manual.stream().mapToInt(Integer::intValue).average().orElse(0);
            double avg = (system.isEmpty() ? 0 : systemAvg * systemWeight) + (manual.isEmpty() ? 0 : manualAvg * manualWeight);
            Integer latest = roomScores.stream().max(Comparator.comparing(HygieneScore::getScoreDate).thenComparing(HygieneScore::getId)).map(HygieneScore::getScore).orElse(null);

            HygieneRankingItemVO vo = new HygieneRankingItemVO();
            vo.setRoomId(roomId);
            vo.setRoomDisplay(dormRoomService.formatRoomDisplay(dormRoomService.getOrThrow(roomId)));
            vo.setAvgScore(avg);
            vo.setLatestScore(latest);
            if (avg >= red) {
                vo.setBoardType("RED");
            } else if (avg < black) {
                vo.setBoardType("BLACK");
            } else {
                vo.setBoardType("NORMAL");
            }
            items.add(vo);
        }
        items.sort(Comparator.comparing(HygieneRankingItemVO::getAvgScore, Comparator.nullsLast(Comparator.reverseOrder())));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }
        return items;
    }

    public String exportMonthRankingCsv(String month) {
        List<HygieneRankingItemVO> list = monthRanking(month);
        StringBuilder sb = new StringBuilder();
        sb.append("rank,roomId,roomDisplay,avgScore,latestScore,boardType\n");
        for (HygieneRankingItemVO r : list) {
            sb.append(r.getRank()).append(',')
                    .append(r.getRoomId()).append(',')
                    .append(escapeCsv(r.getRoomDisplay())).append(',')
                    .append(r.getAvgScore() == null ? "" : String.format(Locale.ROOT, "%.2f", r.getAvgScore())).append(',')
                    .append(r.getLatestScore() == null ? "" : r.getLatestScore()).append(',')
                    .append(r.getBoardType())
                    .append('\n');
        }
        return sb.toString();
    }

    public List<HygieneRankingItemVO> weekRanking(String weekStart) {
        LocalDate start = weekStart == null || weekStart.isBlank() ? LocalDate.now().with(DayOfWeek.MONDAY) : parseDateOrThrow(weekStart);
        LocalDate end = start.plusDays(6);

        double systemWeight = getRuleDouble("hygiene.systemWeight", 0.4);
        double manualWeight = getRuleDouble("hygiene.manualWeight", 0.6);
        int red = getRuleInt("hygiene.redThreshold", 90);
        int black = getRuleInt("hygiene.blackThreshold", 70);

        List<HygieneScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<HygieneScore>()
                        .ge(HygieneScore::getScoreDate, start)
                        .le(HygieneScore::getScoreDate, end));
        Map<Long, List<HygieneScore>> byRoom = scores.stream().collect(Collectors.groupingBy(HygieneScore::getRoomId));

        List<HygieneRankingItemVO> items = new ArrayList<>();
        for (Map.Entry<Long, List<HygieneScore>> e : byRoom.entrySet()) {
            Long roomId = e.getKey();
            List<HygieneScore> roomScores = e.getValue();
            List<Integer> system = roomScores.stream()
                    .filter(s -> "SYSTEM".equals(s.getSourceType()))
                    .map(HygieneScore::getScore).filter(Objects::nonNull).toList();
            List<Integer> manual = roomScores.stream()
                    .filter(s -> "DORM_MANAGER".equals(s.getSourceType()))
                    .map(HygieneScore::getScore).filter(Objects::nonNull).toList();

            double systemAvg = system.isEmpty() ? 0 : system.stream().mapToInt(Integer::intValue).average().orElse(0);
            double manualAvg = manual.isEmpty() ? 0 : manual.stream().mapToInt(Integer::intValue).average().orElse(0);
            double avg = (system.isEmpty() ? 0 : systemAvg * systemWeight) + (manual.isEmpty() ? 0 : manualAvg * manualWeight);
            Integer latest = roomScores.stream().max(Comparator.comparing(HygieneScore::getScoreDate).thenComparing(HygieneScore::getId)).map(HygieneScore::getScore).orElse(null);

            HygieneRankingItemVO vo = new HygieneRankingItemVO();
            vo.setRoomId(roomId);
            vo.setRoomDisplay(dormRoomService.formatRoomDisplay(dormRoomService.getOrThrow(roomId)));
            vo.setAvgScore(avg);
            vo.setLatestScore(latest);
            if (avg >= red) {
                vo.setBoardType("RED");
            } else if (avg < black) {
                vo.setBoardType("BLACK");
            } else {
                vo.setBoardType("NORMAL");
            }
            items.add(vo);
        }
        items.sort(Comparator.comparing(HygieneRankingItemVO::getAvgScore, Comparator.nullsLast(Comparator.reverseOrder())));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }
        return items;
    }

    public String exportWeekRankingCsv(String weekStart) {
        List<HygieneRankingItemVO> list = weekRanking(weekStart);
        StringBuilder sb = new StringBuilder();
        sb.append("rank,roomId,roomDisplay,avgScore,latestScore,boardType\n");
        for (HygieneRankingItemVO r : list) {
            sb.append(r.getRank()).append(',')
                    .append(r.getRoomId()).append(',')
                    .append(escapeCsv(r.getRoomDisplay())).append(',')
                    .append(r.getAvgScore() == null ? "" : String.format(Locale.ROOT, "%.2f", r.getAvgScore())).append(',')
                    .append(r.getLatestScore() == null ? "" : r.getLatestScore()).append(',')
                    .append(r.getBoardType())
                    .append('\n');
        }
        return sb.toString();
    }

    @Transactional
    public void dailySystemClose(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        int missPenaltyScore = getRuleInt("hygiene.missPenaltyScore", 60);
        int doneScore = getRuleInt("hygiene.doneScore", 100);

        List<HygieneDutyTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<HygieneDutyTask>()
                        .eq(HygieneDutyTask::getDutyDate, target));
        if (tasks.isEmpty()) {
            return;
        }

        Map<Long, List<HygieneDutyTask>> byRoom = tasks.stream().collect(Collectors.groupingBy(HygieneDutyTask::getRoomId));
        for (Map.Entry<Long, List<HygieneDutyTask>> entry : byRoom.entrySet()) {
            Long roomId = entry.getKey();
            List<HygieneDutyTask> roomTasks = entry.getValue();

            int total = roomTasks.size();
            int completed = (int) roomTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
            boolean allDone = total > 0 && completed == total;

            if (!allDone) {
                for (HygieneDutyTask t : roomTasks) {
                    if (!"COMPLETED".equals(t.getStatus())) {
                        t.setStatus("OVERDUE");
                        taskMapper.updateById(t);
                        createRoomRp(roomId, t.getDutyUserId(), "PUNISH", -1, "未按时完成卫生打卡（系统）", null, 0L);
                    }
                }
            }

            HygieneScore score = new HygieneScore();
            score.setRoomId(roomId);
            score.setScoreDate(target);
            score.setPeriodType("DAILY");
            score.setSourceType("SYSTEM");
            score.setScore(allDone ? doneScore : missPenaltyScore);
            score.setReason(allDone ? "值日任务已完成（系统）" : "存在未完成值日任务（系统）");
            score.setInspectorId(0L);
            scoreMapper.insert(score);
        }
    }

    private List<HygieneTaskVO> toTaskVOList(Long roomId, List<HygieneDutyTask> tasks) {
        if (tasks.isEmpty()) return List.of();
        DormRoom room = dormRoomService.getOrThrow(roomId);
        String roomDisplay = dormRoomService.formatRoomDisplay(room);

        Set<Long> dutyUserIds = tasks.stream().map(HygieneDutyTask::getDutyUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameById = dutyUserIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(dutyUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getRealName() == null ? u.getUsername() : u.getRealName(), (a, b) -> a));

        Set<Long> taskIds = tasks.stream().map(HygieneDutyTask::getId).collect(Collectors.toSet());
        Map<Long, HygieneCheckin> checkinByTask = checkinMapper.selectList(
                        new LambdaQueryWrapper<HygieneCheckin>()
                                .in(HygieneCheckin::getTaskId, taskIds)
                                .orderByDesc(HygieneCheckin::getId))
                .stream()
                .collect(Collectors.toMap(HygieneCheckin::getTaskId, c -> c, (a, b) -> a));

        List<HygieneTaskVO> vos = new ArrayList<>();
        for (HygieneDutyTask t : tasks) {
            HygieneTaskVO vo = new HygieneTaskVO();
            vo.setId(t.getId());
            vo.setRoomId(t.getRoomId());
            vo.setRoomDisplay(roomDisplay);
            vo.setDutyDate(t.getDutyDate());
            vo.setDutyUserId(t.getDutyUserId());
            vo.setDutyUserName(userNameById.getOrDefault(t.getDutyUserId(), String.valueOf(t.getDutyUserId())));
            vo.setDutyItem(t.getDutyItem());
            vo.setStatus(t.getStatus());
            vo.setDeadlineTime(t.getDeadlineTime());
            HygieneCheckin c = checkinByTask.get(t.getId());
            vo.setCheckedIn(c != null);
            if (c != null) {
                vo.setCheckinId(c.getId());
                vo.setCheckinPhotoUrl(c.getPhotoUrl());
                vo.setCheckinTime(c.getCheckinTime());
                vo.setVerifyStatus(c.getVerifyStatus());
                vo.setCheckinLocationText(c.getLocationText());
                vo.setCheckinRemark(c.getRemark());
            }
            vos.add(vo);
        }
        return vos;
    }

    private HygieneScoreItemVO toScoreVO(HygieneScore s) {
        HygieneScoreItemVO vo = new HygieneScoreItemVO();
        vo.setId(s.getId());
        vo.setRoomId(s.getRoomId());
        vo.setScoreDate(s.getScoreDate());
        vo.setPeriodType(s.getPeriodType());
        vo.setScore(s.getScore());
        vo.setSourceType(s.getSourceType());
        vo.setReason(s.getReason());
        vo.setInspectorId(s.getInspectorId());
        return vo;
    }

    private void createRoomRp(Long roomId, Long userId, String rpType, Integer points, String reason, Long relatedScoreId, Long createdBy) {
        HygieneRewardPunishment rp = new HygieneRewardPunishment();
        rp.setRoomId(roomId);
        rp.setUserId(userId);
        rp.setRpType(rpType);
        rp.setPoints(points);
        rp.setReason(reason);
        rp.setRelatedScoreId(relatedScoreId);
        rp.setCreatedBy(createdBy);
        rpMapper.insert(rp);
    }

    private LocalDate parseDateOrThrow(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessException("日期不能为空");
        try {
            return LocalDate.parse(raw.trim(), DATE);
        } catch (Exception e) {
            throw new BusinessException("日期格式须为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseDeadlineOrDefault(LocalDate dutyDate, String raw) {
        if (raw == null || raw.isBlank()) {
            int deadlineHour = getRuleInt("hygiene.deadlineHour", 22);
            return dutyDate.atTime(deadlineHour, 0);
        }
        String v = raw.trim();
        try {
            if (v.contains("T")) return LocalDateTime.parse(v);
            if (v.contains(" ")) return LocalDateTime.parse(v.replace(" ", "T"));
            throw new BusinessException("截止时间格式须为 yyyy-MM-ddTHH:mm");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("截止时间格式须为 yyyy-MM-ddTHH:mm");
        }
    }

    private Long getCurrentRoomIdOrThrow(Long studentId) {
        List<AssignmentResult> candidates = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getStudentId, studentId)
                        .orderByDesc(AssignmentResult::getBatchId));
        for (AssignmentResult r : candidates) {
            AssignmentBatch b = batchMapper.selectById(r.getBatchId());
            if (b != null && "PUBLISHED".equals(b.getStatus())) {
                return r.getRoomId();
            }
        }
        throw new BusinessException("未找到已公示的分配结果，无法确定宿舍");
    }

    private List<Long> listRoomMemberIdsOrThrow(Long anyStudentIdInRoom) {
        List<AssignmentResult> candidates = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getStudentId, anyStudentIdInRoom)
                        .orderByDesc(AssignmentResult::getBatchId));
        AssignmentResult myResult = null;
        AssignmentBatch batch = null;
        for (AssignmentResult r : candidates) {
            AssignmentBatch b = batchMapper.selectById(r.getBatchId());
            if (b != null && "PUBLISHED".equals(b.getStatus())) {
                myResult = r;
                batch = b;
                break;
            }
        }
        if (myResult == null || batch == null) {
            throw new BusinessException("未找到已公示的分配结果");
        }
        return resultMapper.selectList(
                        new LambdaQueryWrapper<AssignmentResult>()
                                .eq(AssignmentResult::getBatchId, myResult.getBatchId())
                                .eq(AssignmentResult::getRoomId, myResult.getRoomId()))
                .stream().map(AssignmentResult::getStudentId).toList();
    }

    private void requireLeader(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null || !UserType.STUDENT.name().equals(u.getUserType())) {
            throw new BusinessException("仅学生可执行该操作");
        }
        SysUserStudent stu = sysUserStudentMapper.selectById(userId);
        if (stu == null || !Boolean.TRUE.equals(stu.getLeader())) {
            throw new BusinessException("仅宿舍长可执行该操作");
        }
    }

    private void requireDormManager(Long managerId, Long roomId) {
        User u = userMapper.selectById(managerId);
        if (u == null || !UserType.DORM_MANAGER.name().equals(u.getUserType())) {
            throw new BusinessException("仅宿管可执行该操作");
        }
        List<DormRoomVO> rooms = dormRoomService.listRoomsByManager(managerId);
        boolean ok = rooms.stream().anyMatch(r -> Objects.equals(r.getId(), roomId));
        if (!ok) {
            throw new BusinessException("无权操作该房间");
        }
    }

    private int getRuleInt(String key, int def) {
        HygieneRule r = ruleMapper.selectOne(new LambdaQueryWrapper<HygieneRule>().eq(HygieneRule::getRuleKey, key).last("limit 1"));
        if (r == null || r.getRuleValue() == null) return def;
        try {
            return Integer.parseInt(r.getRuleValue().trim());
        } catch (Exception e) {
            return def;
        }
    }

    private double getRuleDouble(String key, double def) {
        HygieneRule r = ruleMapper.selectOne(new LambdaQueryWrapper<HygieneRule>().eq(HygieneRule::getRuleKey, key).last("limit 1"));
        if (r == null || r.getRuleValue() == null) return def;
        try {
            return Double.parseDouble(r.getRuleValue().trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String escapeCsv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
