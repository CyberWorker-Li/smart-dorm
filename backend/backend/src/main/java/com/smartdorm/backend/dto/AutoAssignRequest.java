package com.smartdorm.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AutoAssignRequest {

    /**
     * FULL：本批次全量重分；
     * SAME_ACADEMIC_YEAR：仅重分学生档案学年与批次学年一致者；
     * INCREMENTAL：保留已有分配，只给尚未分配的学生补位。
     */
    private String reassignScope;

    /** 若指定，则只在某栋楼内的有效房间中分配 */
    private Long targetBuildingId;

    /** 若指定，仅对这些尚未分配床位的学生进行分配（内部用于批量换楼等） */
    private List<Long> restrictStudentIds;
}
