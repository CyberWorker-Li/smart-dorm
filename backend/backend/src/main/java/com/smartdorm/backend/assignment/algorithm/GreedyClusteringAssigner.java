package com.smartdorm.backend.assignment.algorithm;

import com.smartdorm.backend.entity.DormRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GreedyClusteringAssigner {

    private final WeightedSimilarityCalculator similarityCalc;

    private static final double SIMILARITY_WEIGHT = 1.0;
    private static final double FILL_WEIGHT = 0.25;
    private static final double EMPTY_ROOM_PENALTY = 0.35;

    /**
     * 贪心聚类分配
     * @param candidates 待分配的学生列表（已按性别等硬约束筛选）
     * @param rooms      可用房间列表（已校验容量、性别）
     * @return Map<学生ID, 房间ID>
     */
    public Map<Long, Long> assign(List<StudentWithAnswer> candidates, List<DormRoom> rooms) {
        if (candidates.isEmpty() || rooms.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, StudentWithAnswer> idToStudent = candidates.stream()
                .collect(Collectors.toMap(StudentWithAnswer::getStudentId, s -> s, (a, b) -> a));

        // 房间剩余容量（以及初始容量，用于计算“已填充比例”）
        Map<Long, Integer> remainingCap = rooms.stream()
                .collect(Collectors.toMap(DormRoom::getId, DormRoom::getCapacity, (a, b) -> a));
        Map<Long, Integer> initialCap = new HashMap<>(remainingCap);

        // 深拷贝待分配列表
        List<StudentWithAnswer> unassigned = new ArrayList<>(candidates);
        Map<Long, Long> assignment = new HashMap<>();

        // 按房间暂存已分配的学生ID列表
        Map<Long, List<Long>> roomStudents = new HashMap<>();

        while (!unassigned.isEmpty()) {
            Long bestStudentId = null;
            Long bestRoomId = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            // 遍历所有未分配学生与可用房间
            for (StudentWithAnswer student : unassigned) {
                for (DormRoom room : rooms) {
                    if (remainingCap.getOrDefault(room.getId(), 0) <= 0) {
                        continue;
                    }
                    Long roomId = room.getId();
                    int init = Math.max(1, initialCap.getOrDefault(roomId, 1));
                    int rem = Math.max(0, remainingCap.getOrDefault(roomId, 0));
                    int used = init - rem;
                    double fillRatio = used / (double) init;

                    int matesCount = roomStudents.getOrDefault(roomId, Collections.emptyList()).size();
                    double sim = computeAvgSimilarityToRoom(student, roomId, roomStudents, idToStudent);
                    // 优先填满房间：对“开启新房间（空房）”施加惩罚，并对已填充房间加分
                    double score = SIMILARITY_WEIGHT * sim + FILL_WEIGHT * fillRatio - (matesCount == 0 ? EMPTY_ROOM_PENALTY : 0.0);

                    if (score > bestScore) {
                        bestScore = score;
                        bestStudentId = student.getStudentId();
                        bestRoomId = roomId;
                        continue;
                    }
                    if (score == bestScore && bestRoomId != null) {
                        // 得分相同：更偏向已更“满”的房间，减少房间占用数量
                        int bestInit = Math.max(1, initialCap.getOrDefault(bestRoomId, 1));
                        int bestRem = Math.max(0, remainingCap.getOrDefault(bestRoomId, 0));
                        int bestUsed = bestInit - bestRem;
                        if (used > bestUsed) {
                            bestStudentId = student.getStudentId();
                            bestRoomId = roomId;
                        }
                    }
                }
            }

            if (bestStudentId == null || bestRoomId == null) {
                // 无合适房间，退出（实际不会发生，因为剩余容量总和应≥学生数）
                break;
            }

            // 执行分配
            assignment.put(bestStudentId, bestRoomId);
            roomStudents.computeIfAbsent(bestRoomId, k -> new ArrayList<>()).add(bestStudentId);
            remainingCap.put(bestRoomId, remainingCap.get(bestRoomId) - 1);

            // 从未分配列表中移除
            Long selectedStudentId = bestStudentId;
            unassigned.removeIf(s -> s.getStudentId().equals(selectedStudentId));
        }

        return assignment;
    }

    /**
     * 计算学生与房间内已有学生的平均相似度
     * @param student     待分配学生
     * @param roomId      目标房间
     * @param roomStudents 房间->已分配学生ID列表
     * @param allCandidates 所有候选学生（用于通过ID获取StudentWithAnswer对象）
     * @return 平均相似度（空房间返回1.0）
     */
    private double computeAvgSimilarityToRoom(StudentWithAnswer student,
                                              Long roomId,
                                              Map<Long, List<Long>> roomStudents,
                                              Map<Long, StudentWithAnswer> idToStudent) {
        List<Long> mates = roomStudents.getOrDefault(roomId, Collections.emptyList());
        if (mates.isEmpty()) {
            // 空房间相似度视为中性（再由上层策略控制是否“开新房”）
            return 0.5;
        }

        double sum = 0.0;
        for (Long mateId : mates) {
            StudentWithAnswer mate = idToStudent.get(mateId);
            if (mate != null) {
                sum += similarityCalc.compute(student.getAnswer(), mate.getAnswer());
            }
        }
        return sum / mates.size();
    }
}
