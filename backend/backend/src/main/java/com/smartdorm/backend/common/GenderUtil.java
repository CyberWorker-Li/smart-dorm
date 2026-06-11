package com.smartdorm.backend.common;

import com.smartdorm.backend.entity.User;

public final class GenderUtil {

    private GenderUtil() {
    }

    public static String requireCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("性别不能为空（MALE 男 / FEMALE 女）");
        }
        String u = raw.trim().toUpperCase();
        if (!"MALE".equals(u) && !"FEMALE".equals(u)) {
            throw new BusinessException("性别只能为 MALE（男）或 FEMALE（女）");
        }
        return u;
    }

    /** 学生/宿管 与 楼房性别（MALE/FEMALE）是否一致 */
    public static void requireUserMatchesBuildingGender(User user, String buildingGender, boolean managerContext) {
        if (user == null) {
            throw new BusinessException(managerContext ? "宿管不存在" : "学生不存在");
        }
        String userGender = requireCode(user.getGender());
        String bg = requireCode(buildingGender);
        if (!userGender.equals(bg)) {
            throw new BusinessException(managerContext
                    ? "宿管性别须与楼房性别类型一致（男宿管→男生楼，女宿管→女生楼）"
                    : "学生性别须与楼房性别类型一致（男生→男生楼，女生→女生楼）");
        }
    }
}
