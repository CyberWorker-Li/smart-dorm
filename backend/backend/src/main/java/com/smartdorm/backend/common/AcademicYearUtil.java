package com.smartdorm.backend.common;

import java.util.regex.Pattern;

public final class AcademicYearUtil {

    private static final Pattern PATTERN = Pattern.compile("^\\d{4}-\\d{4}$");

    private AcademicYearUtil() {
    }

    /** 校验学年格式为 YYYY-YYYY，且后一年 = 前一年 + 1，如 2026-2027 */
    public static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("学年不能为空");
        }
        String s = raw.trim();
        if (!PATTERN.matcher(s).matches()) {
            throw new BusinessException("学年格式须为四位年份-四位年份，例如 2026-2027");
        }
        String[] parts = s.split("-");
        int y1 = Integer.parseInt(parts[0]);
        int y2 = Integer.parseInt(parts[1]);
        if (y2 != y1 + 1) {
            throw new BusinessException("学年后一年须为前一年加 1，例如 2026-2027");
        }
        return s;
    }
}
