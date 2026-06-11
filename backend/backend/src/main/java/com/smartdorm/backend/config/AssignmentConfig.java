package com.smartdorm.backend.config;

/**
 * 精准分配算法配置常量
 * 所有维度权重之和应为 1.0
 */
public class AssignmentConfig {

    // ========== 维度权重 ==========
    /** 作息时间（起床+睡觉）权重 */
    public static final double WAKE_SLEEP_WEIGHT = 0.25;

    /** 卫生习惯（是否吸烟、是否爱干净）权重 */
    public static final double CLEAN_SMOKE_WEIGHT = 0.20;

    /** 熬夜习惯权重 */
    public static final double STAY_UP_WEIGHT = 0.10;

    /** 性格匹配权重 */
    public static final double PERSONALITY_WEIGHT = 0.15;

    /** 兴趣爱好相似度权重 */
    public static final double HOBBY_WEIGHT = 0.15;

    /** 家乡/专业相似度权重 */
    public static final double HOMETOWN_MAJOR_WEIGHT = 0.10;

    /** 自我描述与宿舍期望文本相似度权重 */
    public static final double SELF_EXPECT_WEIGHT = 0.05;

    // ========== 冲突阈值 ==========
    /** 冲突分数高于此值则标记为高冲突（1-相似度） */
    public static final double CONFLICT_THRESHOLD = 0.7;

    // ========== 时间归一化参数 ==========
    /** 最大时间差（分钟），用于作息差异归一化，默认8小时=480分钟 */
    public static final int MAX_TIME_DIFF_MINUTES = 480;

    private AssignmentConfig() {} // 禁止实例化
}