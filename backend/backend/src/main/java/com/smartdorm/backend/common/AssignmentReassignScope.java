package com.smartdorm.backend.common;

public final class AssignmentReassignScope {

    private AssignmentReassignScope() {
    }

    /** 清空本批次全部分配后重新分配本学年全部学生 */
    public static final String FULL = "FULL";

    /** 仅清除本批次中学年（学生档案）与批次学年一致的学生分配，再为这些人重分；其余学生本批次记录不动 */
    public static final String SAME_ACADEMIC_YEAR = "SAME_ACADEMIC_YEAR";

    /** 不清除已有记录，仅为尚未在本批次中分配床位的学生分配 */
    public static final String INCREMENTAL = "INCREMENTAL";

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        String u = raw.trim().toUpperCase();
        if (FULL.equals(u) || SAME_ACADEMIC_YEAR.equals(u) || INCREMENTAL.equals(u)) {
            return u;
        }
        throw new BusinessException("reassignScope 须为 FULL、SAME_ACADEMIC_YEAR 或 INCREMENTAL");
    }
}
