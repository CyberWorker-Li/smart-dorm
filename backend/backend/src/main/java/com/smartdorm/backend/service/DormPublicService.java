package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.DormPublicRoomVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DormPublicService {

    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final DormRoomMapper dormRoomMapper;
    private final DormFloorMapper dormFloorMapper;
    private final DormBuildingMapper dormBuildingMapper;
    private final HygieneScoreMapper hygieneScoreMapper;
    private final QuestionnaireAnswerMapper answerMapper;
    private final SwapIntentMapper swapIntentMapper;

    private Long resolveBatchId(Long batchId) {
        if (batchId != null) {
            AssignmentBatch b = batchMapper.selectById(batchId);
            if (b == null) throw new BusinessException("分配批次不存在");
            if (!"PUBLISHED".equals(b.getStatus())) throw new BusinessException("仅支持已公示批次");
            return b.getId();
        }
        AssignmentBatch latest = batchMapper.selectOne(new LambdaQueryWrapper<AssignmentBatch>()
                .eq(AssignmentBatch::getStatus, "PUBLISHED")
                .orderByDesc(AssignmentBatch::getId)
                .last("LIMIT 1"));
        if (latest == null) throw new BusinessException("暂无已公示批次");
        return latest.getId();
    }

    public List<DormPublicRoomVO> listRooms(Long batchId,
                                           String gender,
                                           Long buildingId,
                                           Integer floorNo,
                                           Boolean hasVacancy,
                                           Boolean hasSwapIntent) {
        Long bid = resolveBatchId(batchId);
        AssignmentBatch batch = batchMapper.selectById(bid);
        Long questionnaireId = batch != null ? batch.getQuestionnaireId() : null;

        List<AssignmentResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, bid));

        Map<Long, Integer> occByRoom = new HashMap<>();
        Map<Long, Long> studentToRoom = new HashMap<>();
        Set<Long> studentIds = new HashSet<>();
        for (AssignmentResult r : results) {
            occByRoom.merge(r.getRoomId(), 1, Integer::sum);
            studentToRoom.put(r.getStudentId(), r.getRoomId());
            studentIds.add(r.getStudentId());
        }

        List<DormRoom> rooms = dormRoomMapper.selectList(null);
        Map<Long, DormFloor> floorById = dormFloorMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormFloor::getId, f -> f, (a, b) -> a));
        Map<Long, DormBuilding> buildingById = dormBuildingMapper.selectList(null).stream()
                .collect(Collectors.toMap(DormBuilding::getId, b -> b, (a, b) -> a));

        String g = gender != null && !gender.isBlank() ? gender.trim().toUpperCase() : null;
        if (g != null && !"MALE".equals(g) && !"FEMALE".equals(g)) g = null;

        Map<Long, Double> hygieneAvg = loadHygieneAvgByRoom();
        Map<Long, String> sleepTag = new HashMap<>();
        Map<Long, String> wakeTag = new HashMap<>();
        if (questionnaireId != null && !studentIds.isEmpty()) {
            Map<Long, QuestionnaireAnswer> answerByStudent = answerMapper.selectList(new LambdaQueryWrapper<QuestionnaireAnswer>()
                            .eq(QuestionnaireAnswer::getQuestionnaireId, questionnaireId)
                            .in(QuestionnaireAnswer::getStudentId, studentIds)
                            .eq(QuestionnaireAnswer::getStatus, "SUBMITTED"))
                    .stream()
                    .collect(Collectors.toMap(QuestionnaireAnswer::getStudentId, a -> a, (a, b) -> a));

            Map<Long, List<QuestionnaireAnswer>> answersByRoom = new HashMap<>();
            for (Long sid : studentIds) {
                Long rid = studentToRoom.get(sid);
                QuestionnaireAnswer a = answerByStudent.get(sid);
                if (rid != null && a != null) {
                    answersByRoom.computeIfAbsent(rid, k -> new ArrayList<>()).add(a);
                }
            }

            for (Map.Entry<Long, List<QuestionnaireAnswer>> e : answersByRoom.entrySet()) {
                Long rid = e.getKey();
                List<QuestionnaireAnswer> list = e.getValue();
                sleepTag.put(rid, calcSleepTag(list));
                wakeTag.put(rid, calcWakeTag(list));
            }
        }

        Map<Long, Integer> intentCountByRoom = new HashMap<>();
        List<SwapIntent> intents = swapIntentMapper.selectList(new LambdaQueryWrapper<SwapIntent>()
                .eq(SwapIntent::getBatchId, bid)
                .eq(SwapIntent::getStatus, "OPEN"));
        for (SwapIntent it : intents) {
            Long rid = studentToRoom.get(it.getStudentId());
            if (rid != null) {
                intentCountByRoom.merge(rid, 1, Integer::sum);
            }
        }

        List<DormPublicRoomVO> out = new ArrayList<>();
        for (DormRoom r : rooms) {
            DormFloor f = floorById.get(r.getFloorId());
            if (f == null) continue;
            DormBuilding b = buildingById.get(f.getBuildingId());
            if (b == null) continue;
            if (!Boolean.TRUE.equals(b.getValid()) || !Boolean.TRUE.equals(f.getValid()) || !Boolean.TRUE.equals(r.getValid())) {
                continue;
            }
            if (g != null && !g.equalsIgnoreCase(b.getGender())) continue;
            if (buildingId != null && !buildingId.equals(b.getId())) continue;
            if (floorNo != null && !floorNo.equals(f.getFloorNo())) continue;

            int cap = r.getCapacity() == null ? 0 : r.getCapacity();
            int occ = occByRoom.getOrDefault(r.getId(), 0);
            int vac = Math.max(0, cap - occ);
            int ic = intentCountByRoom.getOrDefault(r.getId(), 0);
            boolean hasInt = ic > 0;

            if (hasVacancy != null) {
                if (hasVacancy && vac <= 0) continue;
                if (!hasVacancy && vac > 0) continue;
            }
            if (hasSwapIntent != null) {
                if (hasSwapIntent && !hasInt) continue;
                if (!hasSwapIntent && hasInt) continue;
            }

            DormPublicRoomVO vo = new DormPublicRoomVO();
            vo.setBatchId(bid);
            vo.setRoomId(r.getId());
            vo.setBuildingName(b.getName());
            vo.setFloorNo(f.getFloorNo());
            vo.setRoomNo(r.getRoomNo());
            vo.setGender(b.getGender());
            vo.setCapacity(cap);
            vo.setOccupied(occ);
            vo.setVacancies(vac);
            vo.setHygieneAvg(hygieneAvg.get(r.getId()));
            vo.setSleepTag(sleepTag.getOrDefault(r.getId(), "未知"));
            vo.setWakeTag(wakeTag.getOrDefault(r.getId(), "未知"));
            vo.setHasSwapIntent(hasInt);
            vo.setSwapIntentCount(ic);
            out.add(vo);
        }

        out.sort(Comparator
                .comparingInt((DormPublicRoomVO v) -> v.getVacancies() != null ? -v.getVacancies() : 0)
                .thenComparingInt(v -> v.getSwapIntentCount() != null ? -v.getSwapIntentCount() : 0)
                .thenComparingLong(v -> v.getRoomId() != null ? v.getRoomId() : Long.MAX_VALUE));
        return out;
    }

    private Map<Long, Double> loadHygieneAvgByRoom() {
        LocalDate from = LocalDate.now().minusDays(30);
        List<HygieneScore> scores = hygieneScoreMapper.selectList(new LambdaQueryWrapper<HygieneScore>()
                .ge(HygieneScore::getScoreDate, from));
        Map<Long, long[]> acc = new HashMap<>();
        for (HygieneScore s : scores) {
            if (s.getRoomId() == null || s.getScore() == null) continue;
            acc.computeIfAbsent(s.getRoomId(), k -> new long[2]);
            long[] v = acc.get(s.getRoomId());
            v[0] += s.getScore();
            v[1] += 1;
        }
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<Long, long[]> e : acc.entrySet()) {
            long sum = e.getValue()[0];
            long cnt = e.getValue()[1];
            if (cnt > 0) out.put(e.getKey(), sum * 1.0 / cnt);
        }
        return out;
    }

    private Integer parseTimeMinutes(String t, boolean afterMidnightToNextDay) {
        if (t == null || t.isBlank()) return null;
        String s = t.trim();
        String[] parts = s.split(":");
        if (parts.length != 2) return null;
        try {
            int hh = Integer.parseInt(parts[0]);
            int mm = Integer.parseInt(parts[1]);
            if (hh < 0 || hh > 23) return null;
            if (mm < 0 || mm > 59) return null;
            int m = hh * 60 + mm;
            if (afterMidnightToNextDay && m < 12 * 60) {
                m += 24 * 60;
            }
            return m;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String calcSleepTag(List<QuestionnaireAnswer> list) {
        List<Integer> ms = list.stream()
                .map(a -> parseTimeMinutes(a.getSleepTime(), true))
                .filter(Objects::nonNull)
                .toList();
        if (ms.isEmpty()) return "未知";
        double avg = ms.stream().mapToInt(i -> i).average().orElse(0);
        if (avg <= 23 * 60 + 30) return "早睡";
        if (avg >= 24 * 60 + 30) return "夜猫子";
        return "正常";
    }

    private String calcWakeTag(List<QuestionnaireAnswer> list) {
        List<Integer> ms = list.stream()
                .map(a -> parseTimeMinutes(a.getWakeUpTime(), false))
                .filter(Objects::nonNull)
                .toList();
        if (ms.isEmpty()) return "未知";
        double avg = ms.stream().mapToInt(i -> i).average().orElse(0);
        if (avg <= 7 * 60) return "早起";
        if (avg >= 9 * 60) return "晚起";
        return "正常";
    }
}

