package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.assignment.algorithm.ConflictDetector;
import com.smartdorm.backend.assignment.algorithm.GreedyClusteringAssigner;
import com.smartdorm.backend.assignment.algorithm.StudentWithAnswer;
import com.smartdorm.backend.common.AcademicYearUtil;
import com.smartdorm.backend.common.AssignmentReassignScope;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.GenderUtil;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.dto.AutoAssignRequest;
import com.smartdorm.backend.dto.BulkBuildingMoveRequest;
import com.smartdorm.backend.dto.CreateBatchRequest;
import com.smartdorm.backend.dto.ManualAssignRequest;
import com.smartdorm.backend.entity.*;
import com.smartdorm.backend.mapper.*;
import com.smartdorm.backend.vo.AssignmentBatchVO;
import com.smartdorm.backend.vo.MyAssignmentResultVO;
import com.smartdorm.backend.vo.RoommateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    public record AcceptanceIssue(String code, String message, int count, List<Long> sampleIds) {
    }

    public record AcceptanceReport(Long batchId,
                                   String academicYear,
                                   String batchStatus,
                                   int poolStudents,
                                   int assignedTotal,
                                   int assignedInPool,
                                   int unassignedInPool,
                                   int conflictWarnings,
                                   boolean pass,
                                   List<AcceptanceIssue> issues) {
    }

    private final AssignmentBatchMapper batchMapper;
    private final AssignmentResultMapper resultMapper;
    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionnaireAnswerMapper answerMapper;
    private final DormRoomService dormRoomService;
    private final UserMapper userMapper;
    private final SysUserStudentMapper sysUserStudentMapper;
    private final AssignmentConflictWarningMapper conflictWarningMapper;
    private final GreedyClusteringAssigner greedyClusteringAssigner;
    private final ConflictDetector conflictDetector;

    public List<AssignmentBatchVO> listBatches() {
        return batchMapper.selectList(
                        new LambdaQueryWrapper<AssignmentBatch>().orderByDesc(AssignmentBatch::getId))
                .stream().map(this::toBatchVO).toList();
    }

    @Transactional
    public AssignmentBatchVO createBatch(CreateBatchRequest request, Long adminId) {
        Questionnaire q = questionnaireMapper.selectOne(
                new LambdaQueryWrapper<Questionnaire>()
                        .eq(Questionnaire::getId, request.getQuestionnaireId())
                        .eq(Questionnaire::getDeleted, false));
        if (q == null) {
            throw new BusinessException("问卷不存在或已删除");
        }
        if (!"CLOSED".equals(q.getStatus()) && !"PUBLISHED".equals(q.getStatus())) {
            throw new BusinessException("问卷尚未发布或关闭，无法创建分配批次");
        }
        String batchYear = AcademicYearUtil.normalizeAndValidate(request.getAcademicYear());
        String qYear = AcademicYearUtil.normalizeAndValidate(q.getAcademicYear());
        if (!batchYear.equals(qYear)) {
            throw new BusinessException("批次学年须与问卷学年一致（问卷为 " + qYear + "）");
        }

        AssignmentBatch batch = new AssignmentBatch();
        batch.setQuestionnaireId(request.getQuestionnaireId());
        batch.setAcademicYear(batchYear);
        batch.setStatus("DRAFT");
        batch.setRemark(request.getRemark());
        batch.setCreateBy(adminId);
        batchMapper.insert(batch);
        return toBatchVO(batch);
    }

    /**
     * 自动分配：本学年全部学生（含未填问卷者，按随机）；按答卷相似度与同学年优先；仅有效宿舍链；
     * 可指定仅在某楼内分配；可按梯度保留或清除本批次原有结果。
     */
    @Transactional
    public void autoAssign(Long batchId, AutoAssignRequest req) {
        AssignmentBatch batch = getOrThrow(batchId);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException("只有草稿状态的批次才能执行自动分配");
        }
        String scope = AssignmentReassignScope.normalize(req == null ? null : req.getReassignScope());
        Long targetBuildingId = req != null ? req.getTargetBuildingId() : null;
        if (targetBuildingId != null) {
            dormRoomService.getBuildingOrThrow(targetBuildingId);
        }

        String batchYear = AcademicYearUtil.normalizeAndValidate(batch.getAcademicYear());

        // 学生池：启用、学生、档案学年 = 批次学年
        Map<Long, SysUserStudent> profileByUser = loadStudentProfilesForYear(batchYear);
        if (profileByUser.isEmpty()) {
            throw new BusinessException("当前学年没有可分配的学生（请检查学生档案中的学年）");
        }

        applyReassignScope(batchId, batchYear, scope);

        Set<Long> poolIds = profileByUser.keySet();
        Set<Long> alreadyPlaced = resultMapper.selectList(
                        new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId))
                .stream().map(AssignmentResult::getStudentId).collect(Collectors.toSet());
        List<Long> toAssign = poolIds.stream().filter(id -> !alreadyPlaced.contains(id)).sorted().collect(Collectors.toList());
        if (req != null && req.getRestrictStudentIds() != null && !req.getRestrictStudentIds().isEmpty()) {
            Set<Long> rs = new HashSet<>(req.getRestrictStudentIds());
            toAssign = toAssign.stream().filter(rs::contains).toList();
        }
        if (toAssign.isEmpty()) {
            throw new BusinessException("没有需要分配床位的学生（可能均已分配）");
        }

        List<QuestionnaireAnswer> submitted = answerMapper.selectList(
                new LambdaQueryWrapper<QuestionnaireAnswer>()
                        .eq(QuestionnaireAnswer::getQuestionnaireId, batch.getQuestionnaireId())
                        .eq(QuestionnaireAnswer::getStatus, "SUBMITTED"));
        Map<Long, QuestionnaireAnswer> answerByStudent = submitted.stream()
                .collect(Collectors.toMap(QuestionnaireAnswer::getStudentId, a -> a, (a, b) -> a));

        List<DormRoom> rooms = dormRoomService.listAssignableRooms(targetBuildingId);
        if (rooms.isEmpty()) {
            throw new BusinessException("暂无可用房间（请检查楼房/楼层/房间是否均为有效，或目标楼是否有可用房）");
        }
        for (DormRoom r : rooms) {
            GenderUtil.requireCode(r.getBuildingGender());
        }

        Map<Long, User> userById = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, toAssign))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, Integer> occ = currentOccupancy(batchId);

        List<StudentWithAnswer> maleCandidates = new ArrayList<>();
        List<StudentWithAnswer> femaleCandidates = new ArrayList<>();
        for (Long sid : toAssign) {
            User u = userById.get(sid);
            if (u == null) {
                throw new BusinessException("学生不存在，id=" + sid);
            }
            String g = GenderUtil.requireCode(u.getGender());
            QuestionnaireAnswer ans = answerByStudent.get(sid);
            if (ans == null) {
                ans = syntheticAnswer(sid, batch.getQuestionnaireId());
                answerByStudent.put(sid, ans);
            }
            StudentWithAnswer swa = new StudentWithAnswer();
            swa.setStudentId(sid);
            swa.setAnswer(ans);
            swa.setUser(u);
            if ("MALE".equals(g)) {
                maleCandidates.add(swa);
            } else {
                femaleCandidates.add(swa);
            }
        }

        assignByGender(batchId, maleCandidates, rooms, occ, "MALE");
        assignByGender(batchId, femaleCandidates, rooms, occ, "FEMALE");

        rebuildConflictWarnings(batchId, answerByStudent);
    }

    /** 兼容旧接口：全量重分、不限定楼 */
    @Transactional
    public void autoAssign(Long batchId) {
        autoAssign(batchId, null);
    }

    private void applyReassignScope(long batchId, String batchYear, String scope) {
        if (AssignmentReassignScope.FULL.equals(scope)) {
            resultMapper.delete(new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId));
            return;
        }
        if (AssignmentReassignScope.INCREMENTAL.equals(scope)) {
            return;
        }
        if (AssignmentReassignScope.SAME_ACADEMIC_YEAR.equals(scope)) {
            List<AssignmentResult> rows = resultMapper.selectList(
                    new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId));
            for (AssignmentResult r : rows) {
                SysUserStudent su = sysUserStudentMapper.selectById(r.getStudentId());
                if (su != null && batchYear.equals(su.getAcademicYear())) {
                    resultMapper.deleteById(r.getId());
                }
            }
        }
    }

    private Map<Long, Integer> currentOccupancy(long batchId) {
        List<AssignmentResult> rows = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId));
        Map<Long, Integer> m = new HashMap<>();
        for (AssignmentResult r : rows) {
            m.merge(r.getRoomId(), 1, Integer::sum);
        }
        return m;
    }

    private void assignByGender(Long batchId,
                                List<StudentWithAnswer> candidates,
                                List<DormRoom> allRooms,
                                Map<Long, Integer> occupancyMut,
                                String gender) {
        if (candidates.isEmpty()) return;
        List<DormRoom> rooms = allRooms.stream()
                .filter(r -> gender.equals(r.getBuildingGender()))
                .toList();
        if (rooms.isEmpty()) {
            throw new BusinessException("有" + ("MALE".equals(gender) ? "男" : "女") + "生待分配，但没有可用的对应房间");
        }

        List<DormRoom> roomCopies = new ArrayList<>();
        int totalSlots = 0;
        for (DormRoom r : rooms) {
            int cap = r.getCapacity() == null ? 0 : r.getCapacity();
            int occ = occupancyMut.getOrDefault(r.getId(), 0);
            int remaining = Math.max(0, cap - occ);
            if (remaining <= 0) continue;
            DormRoom copy = new DormRoom();
            copy.setId(r.getId());
            copy.setFloorId(r.getFloorId());
            copy.setBuilding(r.getBuilding());
            copy.setRoomNo(r.getRoomNo());
            copy.setCapacity(remaining);
            copy.setValid(r.getValid());
            copy.setBuildingGender(r.getBuildingGender());
            roomCopies.add(copy);
            totalSlots += remaining;
        }

        if (totalSlots < candidates.size()) {
            throw new BusinessException("床位不足：待分配 " + candidates.size() + " 人，但剩余床位仅 " + totalSlots + " 个");
        }

        Map<Long, Long> assigned = greedyClusteringAssigner.assign(candidates, roomCopies);
        if (assigned.size() != candidates.size()) {
            throw new BusinessException("分配未完成：仅分配 " + assigned.size() + " / " + candidates.size());
        }

        for (StudentWithAnswer s : candidates) {
            Long roomId = assigned.get(s.getStudentId());
            if (roomId == null) {
                throw new BusinessException("分配未完成：学生未分配到房间，id=" + s.getStudentId());
            }
            DormRoom room = dormRoomService.getOrThrow(roomId);
            int bedNo = nextBedNo(batchId, roomId, room.getCapacity());
            AssignmentResult row = new AssignmentResult();
            row.setBatchId(batchId);
            row.setStudentId(s.getStudentId());
            row.setRoomId(roomId);
            row.setBedNo(bedNo);
            resultMapper.insert(row);
            occupancyMut.merge(roomId, 1, Integer::sum);
        }
    }

    private void rebuildConflictWarnings(Long batchId, Map<Long, QuestionnaireAnswer> answerByStudentMut) {
        AssignmentBatch batch = getOrThrow(batchId);
        Long questionnaireId = batch.getQuestionnaireId();
        conflictWarningMapper.delete(new LambdaQueryWrapper<AssignmentConflictWarning>().eq(AssignmentConflictWarning::getBatchId, batchId));

        List<AssignmentResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId));
        if (results.isEmpty()) return;

        Map<Long, List<Long>> roomStudents = results.stream()
                .collect(Collectors.groupingBy(AssignmentResult::getRoomId,
                        Collectors.mapping(AssignmentResult::getStudentId, Collectors.toList())));

        for (AssignmentResult r : results) {
            if (!answerByStudentMut.containsKey(r.getStudentId())) {
                QuestionnaireAnswer a = syntheticAnswer(r.getStudentId(), questionnaireId);
                answerByStudentMut.put(r.getStudentId(), a);
            }
        }

        List<AssignmentConflictWarning> warnings = conflictDetector.detect(batchId, roomStudents, answerByStudentMut);
        if (!warnings.isEmpty()) {
            for (AssignmentConflictWarning w : warnings) {
                conflictWarningMapper.insert(w);
            }
        }
    }

    private int nextBedNo(Long batchId, Long roomId, int capacity) {
        List<AssignmentResult> inRoom = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, batchId)
                        .eq(AssignmentResult::getRoomId, roomId));
        int max = 0;
        for (AssignmentResult r : inRoom) {
            if (r.getBedNo() != null && r.getBedNo() > max) {
                max = r.getBedNo();
            }
        }
        if (max >= capacity) {
            throw new BusinessException("房间床位已满，无法继续分配");
        }
        return max + 1;
    }

    private Map<Long, SysUserStudent> loadStudentProfilesForYear(String batchYear) {
        List<SysUserStudent> profiles = sysUserStudentMapper.selectList(
                new LambdaQueryWrapper<SysUserStudent>().eq(SysUserStudent::getAcademicYear, batchYear));
        Map<Long, SysUserStudent> map = new HashMap<>();
        for (SysUserStudent p : profiles) {
            User u = userMapper.selectById(p.getUserId());
            if (u != null && UserType.STUDENT.name().equals(u.getUserType()) && Boolean.TRUE.equals(u.getEnabled())) {
                map.put(p.getUserId(), p);
            }
        }
        return map;
    }

    private QuestionnaireAnswer syntheticAnswer(Long studentId, Long questionnaireId) {
        QuestionnaireAnswer a = new QuestionnaireAnswer();
        a.setQuestionnaireId(questionnaireId);
        a.setStudentId(studentId);
        return a;
    }

    @Transactional
    public void publishBatch(Long batchId) {
        AssignmentBatch batch = getOrThrow(batchId);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException("只有草稿状态的批次才能公示");
        }
        Long count = resultMapper.selectCount(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId));
        if (count == 0) {
            throw new BusinessException("批次尚无分配结果，请先执行自动分配");
        }

        batch.setStatus("PUBLISHED");
        batch.setPublishTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Transactional
    public void manualAssign(ManualAssignRequest request) {
        AssignmentBatch batch = getOrThrow(request.getBatchId());
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException("仅草稿批次支持手动调整分配");
        }

        DormRoom room = dormRoomService.getOrThrow(request.getRoomId());
        if (!Boolean.TRUE.equals(room.getValid())) {
            throw new BusinessException("目标房间无效或未启用");
        }

        User student = userMapper.selectById(request.getStudentId());
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        if (!UserType.STUDENT.name().equals(student.getUserType())) {
            throw new BusinessException("只能为学生调整宿舍分配");
        }
        String bg = dormRoomService.resolveBuildingGender(request.getRoomId());
        GenderUtil.requireUserMatchesBuildingGender(student, bg, false);

        SysUserStudent su = sysUserStudentMapper.selectById(student.getId());
        if (su == null || !batch.getAcademicYear().equals(su.getAcademicYear())) {
            throw new BusinessException("该学生档案学年与本批次学年不一致，不能编入本批次");
        }

        Long occupied = resultMapper.selectCount(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, request.getBatchId())
                        .eq(AssignmentResult::getRoomId, request.getRoomId()));
        AssignmentResult existingSameStudent = resultMapper.selectOne(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, request.getBatchId())
                        .eq(AssignmentResult::getStudentId, request.getStudentId()));

        if (existingSameStudent != null && existingSameStudent.getRoomId().equals(request.getRoomId())) {
            // 同一房间仅更新床位
        } else if (occupied >= room.getCapacity()) {
            throw new BusinessException("目标房间已满");
        }

        Integer bedNo = request.getBedNo();
        if (bedNo == null) {
            bedNo = nextBedNo(request.getBatchId(), request.getRoomId(), room.getCapacity());
        }

        if (existingSameStudent != null) {
            existingSameStudent.setRoomId(request.getRoomId());
            existingSameStudent.setBedNo(bedNo);
            resultMapper.updateById(existingSameStudent);
        } else {
            AssignmentResult r = new AssignmentResult();
            r.setBatchId(request.getBatchId());
            r.setStudentId(request.getStudentId());
            r.setRoomId(request.getRoomId());
            r.setBedNo(bedNo);
            resultMapper.insert(r);
        }
    }

    @Transactional
    public void removeStudentFromBatch(Long batchId, Long studentId) {
        AssignmentBatch batch = getOrThrow(batchId);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException("仅草稿批次支持移出学生");
        }
        resultMapper.delete(new LambdaQueryWrapper<AssignmentResult>()
                .eq(AssignmentResult::getBatchId, batchId)
                .eq(AssignmentResult::getStudentId, studentId));
    }

    /**
     * 将某学年某性别、且当前床位在源楼的学生从本批次结果中移除，并在目标楼内自动分配空床位。
     */
    @Transactional
    public void bulkRelocateBetweenBuildings(BulkBuildingMoveRequest req) {
        String year = AcademicYearUtil.normalizeAndValidate(req.getAcademicYear());
        String gender = GenderUtil.requireCode(req.getGender());
        AssignmentBatch batch = getOrThrow(req.getBatchId());
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException("仅草稿批次支持批量换楼");
        }
        if (!year.equals(AcademicYearUtil.normalizeAndValidate(batch.getAcademicYear()))) {
            throw new BusinessException("请求学年须与本分配批次学年一致");
        }

        DormBuilding srcB = dormRoomService.getBuildingOrThrow(req.getSourceBuildingId());
        DormBuilding tgtB = dormRoomService.getBuildingOrThrow(req.getTargetBuildingId());
        GenderUtil.requireCode(srcB.getGender());
        GenderUtil.requireCode(tgtB.getGender());
        if (!gender.equals(srcB.getGender()) || !gender.equals(tgtB.getGender())) {
            throw new BusinessException("学生性别须与源楼、目标楼的性别类型一致");
        }

        Map<Long, SysUserStudent> pool = loadStudentProfilesForYear(year);
        Set<Long> poolIds = pool.keySet().stream()
                .filter(id -> {
                    User u = userMapper.selectById(id);
                    return u != null && gender.equals(GenderUtil.requireCode(u.getGender()));
                })
                .collect(Collectors.toSet());

        List<AssignmentResult> rows = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, req.getBatchId()));
        Set<Long> removed = new HashSet<>();
        for (AssignmentResult r : rows) {
            if (!poolIds.contains(r.getStudentId())) {
                continue;
            }
            Long bid = dormRoomService.getBuildingIdForRoom(r.getRoomId());
            if (req.getSourceBuildingId().equals(bid)) {
                resultMapper.deleteById(r.getId());
                removed.add(r.getStudentId());
            }
        }
        if (removed.isEmpty()) {
            throw new BusinessException("没有在源楼中找到符合条件的在住学生");
        }

        AutoAssignRequest follow = new AutoAssignRequest();
        follow.setReassignScope(AssignmentReassignScope.INCREMENTAL);
        follow.setTargetBuildingId(req.getTargetBuildingId());
        follow.setRestrictStudentIds(new ArrayList<>(removed));
        autoAssign(req.getBatchId(), follow);
    }

    public MyAssignmentResultVO getMyResult(Long studentId) {
        List<AssignmentResult> candidates = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getStudentId, studentId)
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
        if (myResult == null) {
            return null;
        }

        final AssignmentBatch publishedBatch = batch;
        final Long questionnaireIdForRoommates = publishedBatch.getQuestionnaireId();

        DormRoom room = dormRoomService.getOrThrow(myResult.getRoomId());

        List<AssignmentResult> roommates = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, myResult.getBatchId())
                        .eq(AssignmentResult::getRoomId, myResult.getRoomId()));

        List<RoommateVO> roommateVOs = roommates.stream()
                .filter(r -> !r.getStudentId().equals(studentId))
                .map(r -> buildRoommateVO(r, questionnaireIdForRoommates, true))
                .toList();

        MyAssignmentResultVO vo = new MyAssignmentResultVO();
        vo.setBatchId(publishedBatch.getId());
        vo.setAcademicYear(publishedBatch.getAcademicYear());
        vo.setBuilding(room.getBuilding());
        vo.setRoomNo(dormRoomService.formatRoomDisplay(room));
        vo.setRoomGender(dormRoomService.resolveBuildingGender(room.getId()));
        vo.setBedNo(myResult.getBedNo());
        vo.setRoommates(roommateVOs);
        return vo;
    }

    public List<RoommateVO> getRoomResult(Long batchId, Long roomId) {
        List<AssignmentResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>()
                        .eq(AssignmentResult::getBatchId, batchId)
                        .eq(AssignmentResult::getRoomId, roomId));

        AssignmentBatch batch = batchMapper.selectById(batchId);
        Long questionnaireId = batch != null ? batch.getQuestionnaireId() : null;

        return results.stream().map(r -> buildRoommateVO(r, questionnaireId, false)).toList();
    }

    private AssignmentBatch getOrThrow(Long id) {
        AssignmentBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("分配批次不存在");
        }
        return batch;
    }

    private AssignmentBatchVO toBatchVO(AssignmentBatch batch) {
        AssignmentBatchVO vo = new AssignmentBatchVO();
        vo.setId(batch.getId());
        vo.setQuestionnaireId(batch.getQuestionnaireId());
        vo.setAcademicYear(batch.getAcademicYear());
        vo.setStatus(batch.getStatus());
        vo.setRemark(batch.getRemark());
        vo.setPublishTime(batch.getPublishTime());
        vo.setCreateTime(batch.getCreateTime());
        if (batch.getQuestionnaireId() != null) {
            Questionnaire q = questionnaireMapper.selectById(batch.getQuestionnaireId());
            if (q != null && !Boolean.TRUE.equals(q.getDeleted())) {
                vo.setQuestionnaireTitle(q.getTitle());
            }
        }
        Long total = resultMapper.selectCount(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batch.getId()));
        vo.setTotalAssigned(total.intValue());
        return vo;
    }

    private RoommateVO buildRoommateVO(AssignmentResult result, Long questionnaireId, boolean maskSensitive) {
        RoommateVO vo = new RoommateVO();
        vo.setStudentId(result.getStudentId());
        vo.setBedNo(result.getBedNo());

        User student = userMapper.selectById(result.getStudentId());
        if (student != null) {
            if (maskSensitive) {
                String realName = student.getRealName();
                String nickname = (realName != null && !realName.isBlank())
                        ? realName.trim().charAt(0) + "同学"
                        : "室友";
                vo.setRealName(nickname);
                vo.setUserNo(null);
            } else {
                vo.setRealName(student.getRealName());
                vo.setUserNo(student.getUserNo());
            }
            vo.setGender(student.getGender());
        }

        if (questionnaireId != null) {
            QuestionnaireAnswer answer = answerMapper.selectOne(
                    new LambdaQueryWrapper<QuestionnaireAnswer>()
                            .eq(QuestionnaireAnswer::getQuestionnaireId, questionnaireId)
                            .eq(QuestionnaireAnswer::getStudentId, result.getStudentId()));
            if (answer != null) {
                vo.setWakeUpTime(answer.getWakeUpTime());
                vo.setSleepTime(answer.getSleepTime());
                vo.setPersonality(answer.getPersonality());
                String hobbies = answer.getHobbies();
                if (maskSensitive && hobbies != null && hobbies.length() > 20) {
                    hobbies = hobbies.substring(0, 20) + "…";
                }
                vo.setHobbies(hobbies);
                vo.setHometown(answer.getHometown());
            }
        }
        return vo;
    }

    public List<AssignmentConflictWarning> listConflictWarnings(Long batchId) {
        if (batchId == null) throw new BusinessException(400, "batchId 不能为空");
        return conflictWarningMapper.selectList(new LambdaQueryWrapper<AssignmentConflictWarning>()
                .eq(AssignmentConflictWarning::getBatchId, batchId)
                .orderByDesc(AssignmentConflictWarning::getConflictScore)
                .orderByDesc(AssignmentConflictWarning::getId)
                .last("LIMIT 200"));
    }

    public AcceptanceReport acceptanceReport(Long batchId, boolean strict) {
        if (batchId == null) throw new BusinessException(400, "batchId 不能为空");
        AssignmentBatch batch = getOrThrow(batchId);
        String batchYear = AcademicYearUtil.normalizeAndValidate(batch.getAcademicYear());

        Set<Long> poolIds = loadStudentProfilesForYear(batchYear).keySet();
        List<AssignmentResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getBatchId, batchId));

        Set<Long> assignedIds = results.stream().map(AssignmentResult::getStudentId).collect(Collectors.toSet());
        int assignedInPool = (int) assignedIds.stream().filter(poolIds::contains).count();
        Set<Long> unassignedInPoolSet = new HashSet<>(poolIds);
        unassignedInPoolSet.removeAll(assignedIds);

        Set<Long> roomIds = results.stream().map(AssignmentResult::getRoomId).collect(Collectors.toSet());
        Map<Long, DormRoomService.RoomChainInfo> roomInfo = dormRoomService.getRoomChainInfo(roomIds);

        Map<Long, User> userById = assignedIds.isEmpty()
                ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, assignedIds))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<AcceptanceIssue> issues = new ArrayList<>();

        List<Long> missingUserIds = assignedIds.stream().filter(id -> !userById.containsKey(id)).sorted().toList();
        if (!missingUserIds.isEmpty()) {
            issues.add(new AcceptanceIssue("USER_MISSING", "分配结果中存在不存在的学生ID", missingUserIds.size(), sample(missingUserIds)));
        }

        List<Long> missingRoomIds = roomIds.stream().filter(id -> !roomInfo.containsKey(id)).sorted().toList();
        if (!missingRoomIds.isEmpty()) {
            issues.add(new AcceptanceIssue("ROOM_MISSING", "分配结果中存在不存在的房间ID", missingRoomIds.size(), sample(missingRoomIds)));
        }

        Map<Long, Integer> occByRoom = new HashMap<>();
        Map<Long, Map<Integer, Integer>> bedCountByRoom = new HashMap<>();
        List<Long> badBedStudentIds = new ArrayList<>();
        List<Long> genderMismatchStudentIds = new ArrayList<>();
        List<Long> invalidRoomStudentIds = new ArrayList<>();
        List<Long> nonStudentTypeIds = new ArrayList<>();
        int bedNoNullCount = 0;

        for (AssignmentResult r : results) {
            occByRoom.merge(r.getRoomId(), 1, Integer::sum);

            User u = userById.get(r.getStudentId());
            if (u != null && !UserType.STUDENT.name().equals(u.getUserType())) {
                nonStudentTypeIds.add(r.getStudentId());
            }

            DormRoomService.RoomChainInfo info = roomInfo.get(r.getRoomId());
            if (info != null) {
                boolean validChain = Boolean.TRUE.equals(info.roomValid())
                        && Boolean.TRUE.equals(info.floorValid())
                        && Boolean.TRUE.equals(info.buildingValid());
                if (!validChain) {
                    invalidRoomStudentIds.add(r.getStudentId());
                }
                if (u != null && info.buildingGender() != null && u.getGender() != null) {
                    String ug = u.getGender().trim().toUpperCase();
                    String bg = info.buildingGender().trim().toUpperCase();
                    if (!ug.equals(bg)) {
                        genderMismatchStudentIds.add(r.getStudentId());
                    }
                }

                if (r.getBedNo() == null) {
                    bedNoNullCount++;
                } else {
                    int bedNo = r.getBedNo();
                    int cap = info.capacity() == null ? 0 : info.capacity();
                    if (bedNo <= 0 || (cap > 0 && bedNo > cap)) {
                        badBedStudentIds.add(r.getStudentId());
                    }
                    bedCountByRoom.computeIfAbsent(r.getRoomId(), k -> new HashMap<>())
                            .merge(bedNo, 1, Integer::sum);
                }
            }
        }

        List<Long> overflowRoomIds = new ArrayList<>();
        List<Long> badBedRooms = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : occByRoom.entrySet()) {
            DormRoomService.RoomChainInfo info = roomInfo.get(e.getKey());
            if (info == null || info.capacity() == null) continue;
            if (e.getValue() > info.capacity()) {
                overflowRoomIds.add(e.getKey());
            }
        }
        for (Map.Entry<Long, Map<Integer, Integer>> e : bedCountByRoom.entrySet()) {
            boolean dup = e.getValue().values().stream().anyMatch(v -> v != null && v > 1);
            if (dup) {
                badBedRooms.add(e.getKey());
            }
        }

        if (!overflowRoomIds.isEmpty()) {
            issues.add(new AcceptanceIssue("CAPACITY_OVERFLOW", "存在房间分配人数超过床位数", overflowRoomIds.size(), sample(overflowRoomIds)));
        }
        if (!badBedRooms.isEmpty()) {
            issues.add(new AcceptanceIssue("BED_DUPLICATE", "存在同一房间床位号重复", badBedRooms.size(), sample(badBedRooms)));
        }
        if (!badBedStudentIds.isEmpty()) {
            issues.add(new AcceptanceIssue("BED_OUT_OF_RANGE", "存在床位号越界（<=0 或 >房间容量）", badBedStudentIds.size(), sample(badBedStudentIds)));
        }
        if (bedNoNullCount > 0) {
            issues.add(new AcceptanceIssue("BED_NULL", "存在未填写床位号的分配记录", bedNoNullCount, List.of()));
        }
        if (!genderMismatchStudentIds.isEmpty()) {
            issues.add(new AcceptanceIssue("GENDER_MISMATCH", "存在学生性别与宿舍楼性别不匹配", genderMismatchStudentIds.size(), sample(genderMismatchStudentIds)));
        }
        if (!invalidRoomStudentIds.isEmpty()) {
            issues.add(new AcceptanceIssue("ROOM_INVALID_CHAIN", "存在分配到无效楼房/楼层/房间的记录", invalidRoomStudentIds.size(), sample(invalidRoomStudentIds)));
        }
        if (!nonStudentTypeIds.isEmpty()) {
            issues.add(new AcceptanceIssue("NOT_STUDENT", "存在非学生账号被写入分配结果", nonStudentTypeIds.size(), sample(nonStudentTypeIds)));
        }
        if (strict && !unassignedInPoolSet.isEmpty()) {
            List<Long> sample = sample(unassignedInPoolSet.stream().sorted().toList());
            issues.add(new AcceptanceIssue("POOL_UNASSIGNED", "存在本学年在档启用学生未分配到床位", unassignedInPoolSet.size(), sample));
        }

        int conflictWarnings = conflictWarningMapper.selectCount(
                new LambdaQueryWrapper<AssignmentConflictWarning>().eq(AssignmentConflictWarning::getBatchId, batchId)).intValue();

        Set<String> criticalCodes = Set.of(
                "USER_MISSING", "ROOM_MISSING", "CAPACITY_OVERFLOW", "BED_DUPLICATE", "BED_OUT_OF_RANGE",
                "GENDER_MISMATCH", "ROOM_INVALID_CHAIN", "NOT_STUDENT"
        );
        boolean pass = issues.stream().noneMatch(i -> criticalCodes.contains(i.code()));
        if (strict) {
            pass = pass && issues.stream().noneMatch(i -> "POOL_UNASSIGNED".equals(i.code()));
        }

        return new AcceptanceReport(
                batchId,
                batchYear,
                batch.getStatus(),
                poolIds.size(),
                results.size(),
                assignedInPool,
                unassignedInPoolSet.size(),
                conflictWarnings,
                pass,
                issues
        );
    }

    private static List<Long> sample(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.size() <= 10 ? ids : ids.subList(0, 10);
    }
}
