package com.smartdorm.backend.assignment.algorithm;

import com.smartdorm.backend.config.AssignmentConfig;
import com.smartdorm.backend.entity.AssignmentConflictWarning;
import com.smartdorm.backend.entity.QuestionnaireAnswer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ConflictDetector {

    private final WeightedSimilarityCalculator similarityCalc;

    /**
     * 检测某批次中所有房间内的冲突对
     * @param batchId        批次ID
     * @param roomToStudents 房间ID -> 该房间学生ID列表
     * @param answerMap      学生ID -> 问卷答案
     * @return 冲突预警列表
     */
    public List<AssignmentConflictWarning> detect(Long batchId,
                                                   Map<Long, List<Long>> roomToStudents,
                                                   Map<Long, QuestionnaireAnswer> answerMap) {
        List<AssignmentConflictWarning> warnings = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : roomToStudents.entrySet()) {
            Long roomId = entry.getKey();
            List<Long> students = entry.getValue();
            if (students.size() < 2) continue;

            // 两两比较
            for (int i = 0; i < students.size(); i++) {
                for (int j = i + 1; j < students.size(); j++) {
                    Long sid1 = students.get(i);
                    Long sid2 = students.get(j);
                    QuestionnaireAnswer a1 = answerMap.get(sid1);
                    QuestionnaireAnswer a2 = answerMap.get(sid2);
                    if (a1 == null || a2 == null) continue;

                    double similarity = similarityCalc.compute(a1, a2);
                    double conflictScore = 1.0 - similarity; // 相似度越低冲突越高
                    if (conflictScore > AssignmentConfig.CONFLICT_THRESHOLD) {
                        AssignmentConflictWarning warn = new AssignmentConflictWarning();
                        warn.setBatchId(batchId);
                        warn.setRoomId(roomId);
                        warn.setStudentId1(sid1);
                        warn.setStudentId2(sid2);
                        warn.setConflictScore(conflictScore);
                        warn.setDescription(buildDescription(a1, a2));
                        warn.setStatus("PENDING");
                        warnings.add(warn);
                    }
                }
            }
        }
        return warnings;
    }

    private String buildDescription(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        List<String> reasons = new ArrayList<>();
        if (!Objects.equals(a.getWakeUpTime(), b.getWakeUpTime()))
            reasons.add("作息时间差异大");
        if (!Objects.equals(a.getSleepTime(), b.getSleepTime()))
            reasons.add("就寝时间差异大");
        if (Boolean.TRUE.equals(a.getSmoke()) != Boolean.TRUE.equals(b.getSmoke()))
            reasons.add("吸烟习惯冲突");
        if (Boolean.TRUE.equals(a.getKeepClean()) != Boolean.TRUE.equals(b.getKeepClean()))
            reasons.add("卫生习惯差异");
        if (Boolean.TRUE.equals(a.getStayUpLate()) != Boolean.TRUE.equals(b.getStayUpLate()))
            reasons.add("熬夜习惯差异");
        if (!Objects.equals(a.getPersonality(), b.getPersonality()))
            reasons.add("性格类型不同");
        // 可继续扩展
        if (reasons.isEmpty()) reasons.add("综合评分偏低");
        return String.join("、", reasons);
    }
}
