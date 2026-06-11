package com.smartdorm.backend.assignment.algorithm;

import com.smartdorm.backend.config.AssignmentConfig;
import com.smartdorm.backend.entity.QuestionnaireAnswer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class WeightedSimilarityCalculator {

    /**
     * 计算两个学生的综合相似度（0~1，越高越适合同一寝室）
     */
    public double compute(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        if (a == null || b == null) return 0.0;
        double score = 0.0;
        score += wakeSleepSimilarity(a, b) * AssignmentConfig.WAKE_SLEEP_WEIGHT;
        score += cleanSmokeSimilarity(a, b) * AssignmentConfig.CLEAN_SMOKE_WEIGHT;
        score += stayUpSimilarity(a, b) * AssignmentConfig.STAY_UP_WEIGHT;
        score += personalitySimilarity(a, b) * AssignmentConfig.PERSONALITY_WEIGHT;
        score += hobbySimilarity(a, b) * AssignmentConfig.HOBBY_WEIGHT;
        score += hometownMajorSimilarity(a, b) * AssignmentConfig.HOMETOWN_MAJOR_WEIGHT;
        score += selfExpectSimilarity(a, b) * AssignmentConfig.SELF_EXPECT_WEIGHT;
        return Math.min(1.0, Math.max(0.0, score));
    }

    // ---------- 各维度相似度计算 ----------

    /** 作息时间相似度：起床和睡觉时间分别取差值，归一化后平均 */
    private double wakeSleepSimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        int diffWake = Math.abs(timeToMinutes(a.getWakeUpTime()) - timeToMinutes(b.getWakeUpTime()));
        int diffSleep = Math.abs(timeToMinutes(a.getSleepTime()) - timeToMinutes(b.getSleepTime()));
        int totalDiff = diffWake + diffSleep;
        double maxDiff = AssignmentConfig.MAX_TIME_DIFF_MINUTES * 2; // 起床+睡觉最大差值
        return 1.0 - Math.min(1.0, totalDiff / maxDiff);
    }

    /** 卫生习惯相似度（吸烟、爱干净） */
    private double cleanSmokeSimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        int same = 0;
        if (Boolean.TRUE.equals(a.getSmoke()) == Boolean.TRUE.equals(b.getSmoke())) same++;
        if (Boolean.TRUE.equals(a.getKeepClean()) == Boolean.TRUE.equals(b.getKeepClean())) same++;
        return same / 2.0;
    }

    /** 熬夜习惯相似度 */
    private double stayUpSimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        return Boolean.TRUE.equals(a.getStayUpLate()) == Boolean.TRUE.equals(b.getStayUpLate()) ? 1.0 : 0.0;
    }

    /** 性格匹配：外向配外向，内向配内向，中性随意 */
    private double personalitySimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        String p1 = a.getPersonality();
        String p2 = b.getPersonality();
        if (p1 == null || p2 == null) return 0.5;
        if (p1.equals(p2)) return 1.0;
        if ("AMBIVERT".equals(p1) || "AMBIVERT".equals(p2)) return 0.8;
        return 0.2;
    }

    /** 兴趣爱好 Jaccard 相似度（按逗号、空格等分割） */
    private double hobbySimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        Set<String> setA = splitHobbies(a.getHobbies());
        Set<String> setB = splitHobbies(b.getHobbies());
        if (setA.isEmpty() && setB.isEmpty()) return 0.5;
        long inter = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    /** 家乡（省份级别） + 专业 相似度 */
    private double hometownMajorSimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        double sim = 0.0;
        if (getProvince(a.getHometown()).equals(getProvince(b.getHometown()))) sim += 0.5;
        if (a.getMajor() != null && a.getMajor().equals(b.getMajor())) sim += 0.5;
        return sim;
    }

    /** 文本相似度（自我描述 + 宿舍期望）简化版 Jaccard */
    private double selfExpectSimilarity(QuestionnaireAnswer a, QuestionnaireAnswer b) {
        String textA = (nullToEmpty(a.getSelfDescription()) + " " + nullToEmpty(a.getDormExpectation())).toLowerCase();
        String textB = (nullToEmpty(b.getSelfDescription()) + " " + nullToEmpty(b.getDormExpectation())).toLowerCase();
        return jaccardWords(textA, textB);
    }

    // ---------- 辅助方法 ----------

    private int timeToMinutes(String time) {
        if (time == null || time.trim().isEmpty()) return 480; // 默认 08:00
        String[] parts = time.split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return hour * 60 + minute;
        } catch (Exception e) {
            return 480;
        }
    }

    private Set<String> splitHobbies(String hobbies) {
        if (hobbies == null) return Collections.emptySet();
        return Arrays.stream(hobbies.split("[，,、\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String getProvince(String hometown) {
        if (hometown == null || hometown.trim().isEmpty()) return "";
        // 简单截取前两个汉字（如“北京市”->“北京”）
        String trimmed = hometown.trim();
        if (trimmed.length() >= 2) {
            return trimmed.substring(0, 2);
        }
        return trimmed;
    }

    private double jaccardWords(String s1, String s2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
        if (words1.isEmpty() && words2.isEmpty()) return 0.5;
        long inter = words1.stream().filter(words2::contains).count();
        long union = words1.size() + words2.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}