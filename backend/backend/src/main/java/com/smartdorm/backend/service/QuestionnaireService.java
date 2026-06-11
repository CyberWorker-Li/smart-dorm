package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.AcademicYearUtil;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.entity.SysUserStudent;
import com.smartdorm.backend.entity.User;
import com.smartdorm.backend.mapper.UserMapper;
import com.smartdorm.backend.dto.CreateQuestionnaireRequest;
import com.smartdorm.backend.dto.SubmitAnswerRequest;
import com.smartdorm.backend.entity.Questionnaire;
import com.smartdorm.backend.entity.QuestionnaireAnswer;
import com.smartdorm.backend.mapper.QuestionnaireAnswerMapper;
import com.smartdorm.backend.mapper.QuestionnaireMapper;
import com.smartdorm.backend.mapper.SysUserStudentMapper;
import com.smartdorm.backend.vo.QuestionnaireVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class QuestionnaireService {

    public record SeedTestAnswersReport(Long questionnaireId,
                                        String academicYear,
                                        int requested,
                                        int studentsPrepared,
                                        int usersCreated,
                                        int answersUpserted,
                                        List<Long> studentIds,
                                        List<String> usernames) {
    }

    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionnaireAnswerMapper answerMapper;
    private final UserMapper userMapper;
    private final SysUserStudentMapper sysUserStudentMapper;

    public List<QuestionnaireVO> listQuestionnaires() {
        List<Questionnaire> list = questionnaireMapper.selectList(
                new LambdaQueryWrapper<Questionnaire>()
                        .eq(Questionnaire::getDeleted, false)
                        .orderByDesc(Questionnaire::getId));
        return list.stream().map(q -> {
            QuestionnaireVO vo = new QuestionnaireVO();
            vo.setId(q.getId());
            vo.setTitle(q.getTitle());
            vo.setAcademicYear(q.getAcademicYear());
            vo.setStatus(q.getStatus());
            vo.setDeadline(q.getDeadline());
            vo.setCreateTime(q.getCreateTime());
            Long count = answerMapper.selectCount(
                    new LambdaQueryWrapper<QuestionnaireAnswer>()
                            .eq(QuestionnaireAnswer::getQuestionnaireId, q.getId())
                            .eq(QuestionnaireAnswer::getStatus, "SUBMITTED"));
            vo.setSubmittedCount(count);
            Long totalStudents = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUserType, UserType.STUDENT.name())
                            .eq(User::getEnabled, true));
            vo.setTotalStudents(totalStudents == null ? 0L : totalStudents);
            long submitted = count == null ? 0L : count;
            vo.setPendingCount(Math.max(0L, vo.getTotalStudents() - submitted));
            return vo;
        }).toList();
    }

    @Transactional
    public QuestionnaireVO createQuestionnaire(CreateQuestionnaireRequest request, Long adminId) {
        String academicYear = AcademicYearUtil.normalizeAndValidate(request.getAcademicYear());
        if (request.getDeadline() == null) {
            throw new BusinessException("创建问卷时必须填写截止时间");
        }

        Questionnaire q = new Questionnaire();
        q.setTitle(request.getTitle().trim());
        q.setAcademicYear(academicYear);
        q.setStatus("DRAFT");
        q.setDeadline(request.getDeadline());
        q.setCreateBy(adminId);
        q.setDeleted(false);
        questionnaireMapper.insert(q);

        if (request.getInheritFromQuestionnaireId() != null) {
            Questionnaire src = questionnaireMapper.selectById(request.getInheritFromQuestionnaireId());
            if (src == null || Boolean.TRUE.equals(src.getDeleted())) {
                throw new BusinessException("继承来源问卷不存在或已删除");
            }
            List<QuestionnaireAnswer> oldAnswers = answerMapper.selectList(
                    new LambdaQueryWrapper<QuestionnaireAnswer>()
                            .eq(QuestionnaireAnswer::getQuestionnaireId, request.getInheritFromQuestionnaireId())
                            .eq(QuestionnaireAnswer::getStatus, "SUBMITTED"));
            for (QuestionnaireAnswer oa : oldAnswers) {
                QuestionnaireAnswer na = copyAnswerRow(oa, q.getId());
                answerMapper.insert(na);
            }
        }

        QuestionnaireVO vo = new QuestionnaireVO();
        vo.setId(q.getId());
        vo.setTitle(q.getTitle());
        vo.setAcademicYear(q.getAcademicYear());
        vo.setStatus(q.getStatus());
        vo.setDeadline(q.getDeadline());
        vo.setCreateTime(q.getCreateTime());
        Long cnt = answerMapper.selectCount(
                new LambdaQueryWrapper<QuestionnaireAnswer>()
                        .eq(QuestionnaireAnswer::getQuestionnaireId, q.getId())
                        .eq(QuestionnaireAnswer::getStatus, "SUBMITTED"));
        vo.setSubmittedCount(cnt);
        return vo;
    }

    private QuestionnaireAnswer copyAnswerRow(QuestionnaireAnswer oa, Long newQuestionnaireId) {
        QuestionnaireAnswer na = new QuestionnaireAnswer();
        na.setQuestionnaireId(newQuestionnaireId);
        na.setStudentId(oa.getStudentId());
        na.setWakeUpTime(oa.getWakeUpTime());
        na.setSleepTime(oa.getSleepTime());
        na.setStayUpLate(oa.getStayUpLate());
        na.setSmoke(oa.getSmoke());
        na.setKeepClean(oa.getKeepClean());
        na.setHometown(oa.getHometown());
        na.setMajor(oa.getMajor());
        na.setPersonality(oa.getPersonality());
        na.setHobbies(oa.getHobbies());
        na.setSelfDescription(oa.getSelfDescription());
        na.setDormExpectation(oa.getDormExpectation());
        na.setStatus("SUBMITTED");
        na.setSubmitTime(oa.getSubmitTime() != null ? oa.getSubmitTime() : LocalDateTime.now());
        return na;
    }

    @Transactional
    public void deleteQuestionnaire(Long id) {
        Questionnaire q = getOrThrow(id);
        q.setDeleted(true);
        questionnaireMapper.updateById(q);
    }

    @Transactional
    public void publishQuestionnaire(Long id) {
        Questionnaire q = getOrThrow(id);
        if (!"DRAFT".equals(q.getStatus())) {
            throw new BusinessException("只有草稿状态的问卷才能发布");
        }
        if (q.getDeadline() == null) {
            throw new BusinessException("问卷缺少截止时间，无法发布");
        }
        q.setStatus("PUBLISHED");
        questionnaireMapper.updateById(q);
    }

    @Transactional
    public void closeQuestionnaire(Long id) {
        Questionnaire q = getOrThrow(id);
        if (!"PUBLISHED".equals(q.getStatus())) {
            throw new BusinessException("只有已发布的问卷才能关闭");
        }
        q.setStatus("CLOSED");
        questionnaireMapper.updateById(q);
    }

    @Transactional
    public void submitAnswer(SubmitAnswerRequest request) {
        Questionnaire q = getOrThrow(request.getQuestionnaireId());
        if (!"PUBLISHED".equals(q.getStatus())) {
            throw new BusinessException("问卷未开放填写");
        }
        if (q.getDeadline() != null && LocalDateTime.now().isAfter(q.getDeadline())) {
            throw new BusinessException("问卷已超过截止时间");
        }

        QuestionnaireAnswer existing = answerMapper.selectOne(
                new LambdaQueryWrapper<QuestionnaireAnswer>()
                        .eq(QuestionnaireAnswer::getQuestionnaireId, request.getQuestionnaireId())
                        .eq(QuestionnaireAnswer::getStudentId, request.getStudentId()));

        if (existing != null) {
            fillAnswer(existing, request);
            existing.setStatus("SUBMITTED");
            existing.setSubmitTime(LocalDateTime.now());
            answerMapper.updateById(existing);
        } else {
            QuestionnaireAnswer answer = new QuestionnaireAnswer();
            fillAnswer(answer, request);
            answer.setStatus("SUBMITTED");
            answer.setSubmitTime(LocalDateTime.now());
            answerMapper.insert(answer);
        }
    }

    public QuestionnaireAnswer getMyAnswer(Long questionnaireId, Long studentId) {
        Questionnaire q = questionnaireMapper.selectById(questionnaireId);
        if (q == null || Boolean.TRUE.equals(q.getDeleted())) {
            return null;
        }
        return answerMapper.selectOne(
                new LambdaQueryWrapper<QuestionnaireAnswer>()
                        .eq(QuestionnaireAnswer::getQuestionnaireId, questionnaireId)
                        .eq(QuestionnaireAnswer::getStudentId, studentId));
    }

    public List<QuestionnaireAnswer> listAnswers(Long questionnaireId) {
        Questionnaire q = questionnaireMapper.selectById(questionnaireId);
        if (q == null || Boolean.TRUE.equals(q.getDeleted())) {
            throw new BusinessException("问卷不存在或已删除");
        }
        return answerMapper.selectList(
                new LambdaQueryWrapper<QuestionnaireAnswer>()
                        .eq(QuestionnaireAnswer::getQuestionnaireId, questionnaireId)
                        .eq(QuestionnaireAnswer::getStatus, "SUBMITTED"));
    }

    private Questionnaire getOrThrow(Long id) {
        Questionnaire q = questionnaireMapper.selectById(id);
        if (q == null || Boolean.TRUE.equals(q.getDeleted())) {
            throw new BusinessException("问卷不存在");
        }
        return q;
    }

    /** 导出答卷为 CSV（UTF-8 BOM，便于 Excel 打开） */
    public byte[] exportAnswersCsv(Long questionnaireId) {
        Questionnaire q = getOrThrow(questionnaireId);
        List<QuestionnaireAnswer> answers = listAnswers(questionnaireId);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("问卷,").append(escapeCsv(q.getTitle())).append('\n');
        sb.append("studentId,wakeUpTime,sleepTime,stayUpLate,smoke,keepClean,hometown,major,personality,hobbies,selfDescription,dormExpectation,submitTime\n");
        for (QuestionnaireAnswer a : answers) {
            sb.append(a.getStudentId()).append(',')
                    .append(escapeCsv(a.getWakeUpTime())).append(',')
                    .append(escapeCsv(a.getSleepTime())).append(',')
                    .append(Boolean.TRUE.equals(a.getStayUpLate()) ? "是" : "否").append(',')
                    .append(Boolean.TRUE.equals(a.getSmoke()) ? "是" : "否").append(',')
                    .append(Boolean.TRUE.equals(a.getKeepClean()) ? "是" : "否").append(',')
                    .append(escapeCsv(a.getHometown())).append(',')
                    .append(escapeCsv(a.getMajor())).append(',')
                    .append(escapeCsv(a.getPersonality())).append(',')
                    .append(escapeCsv(a.getHobbies())).append(',')
                    .append(escapeCsv(a.getSelfDescription())).append(',')
                    .append(escapeCsv(a.getDormExpectation())).append(',')
                    .append(escapeCsv(a.getSubmitTime() == null ? "" : a.getSubmitTime().toString()))
                    .append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private void fillAnswer(QuestionnaireAnswer answer, SubmitAnswerRequest req) {
        answer.setQuestionnaireId(req.getQuestionnaireId());
        answer.setStudentId(req.getStudentId());
        answer.setWakeUpTime(req.getWakeUpTime());
        answer.setSleepTime(req.getSleepTime());
        answer.setStayUpLate(Boolean.TRUE.equals(req.getStayUpLate()));
        answer.setSmoke(Boolean.TRUE.equals(req.getSmoke()));
        answer.setKeepClean(!Boolean.FALSE.equals(req.getKeepClean()));
        answer.setHometown(req.getHometown());
        answer.setMajor(req.getMajor());
        answer.setPersonality(req.getPersonality());
        answer.setHobbies(req.getHobbies());
        answer.setSelfDescription(req.getSelfDescription());
        answer.setDormExpectation(req.getDormExpectation());
    }

    @Transactional
    public SeedTestAnswersReport seedTestAnswers(Long questionnaireId,
                                                 String academicYear,
                                                 Integer count,
                                                 boolean createStudentsIfNeeded) {
        Questionnaire q = getOrThrow(questionnaireId);
        String year = academicYear == null || academicYear.isBlank()
                ? AcademicYearUtil.normalizeAndValidate(q.getAcademicYear())
                : AcademicYearUtil.normalizeAndValidate(academicYear);

        int targetCount = count == null ? 11 : count;
        if (targetCount <= 0 || targetCount > 200) {
            throw new BusinessException(400, "count 必须在 1~200");
        }

        List<SysUserStudent> profiles = sysUserStudentMapper.selectList(
                new LambdaQueryWrapper<SysUserStudent>()
                        .eq(SysUserStudent::getAcademicYear, year)
                        .orderByAsc(SysUserStudent::getUserId));
        List<Long> candidateIds = profiles.stream().map(SysUserStudent::getUserId).filter(Objects::nonNull).toList();
        List<User> candidates = candidateIds.isEmpty()
                ? List.of()
                : userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, candidateIds)
                .eq(User::getUserType, UserType.STUDENT.name())
                .eq(User::getEnabled, true)
                .orderByAsc(User::getId));

        List<User> selected = new ArrayList<>();
        for (User u : candidates) {
            if (selected.size() >= targetCount) break;
            selected.add(u);
        }

        int usersCreated = 0;
        if (selected.size() < targetCount && createStudentsIfNeeded) {
            int need = targetCount - selected.size();
            usersCreated = createSeedStudents(year, need, selected);
        }

        selected.sort(Comparator.comparing(User::getId));
        if (selected.size() > targetCount) {
            selected = new ArrayList<>(selected.subList(0, targetCount));
        }

        List<Long> studentIds = selected.stream().map(User::getId).toList();
        List<String> usernames = selected.stream().map(User::getUsername).toList();

        int upserts = 0;
        List<SeedProfile> templates = defaultSeedProfiles();
        for (int i = 0; i < selected.size(); i++) {
            User u = selected.get(i);
            SeedProfile p = templates.get(i % templates.size());
            SeedProfile effective = p.withGender(fixGender(u.getGender(), p.gender()));

            QuestionnaireAnswer existing = answerMapper.selectOne(
                    new LambdaQueryWrapper<QuestionnaireAnswer>()
                            .eq(QuestionnaireAnswer::getQuestionnaireId, questionnaireId)
                            .eq(QuestionnaireAnswer::getStudentId, u.getId()));

            QuestionnaireAnswer row = existing != null ? existing : new QuestionnaireAnswer();
            row.setQuestionnaireId(questionnaireId);
            row.setStudentId(u.getId());
            row.setWakeUpTime(effective.wakeUpTime());
            row.setSleepTime(effective.sleepTime());
            row.setStayUpLate(effective.stayUpLate());
            row.setSmoke(effective.smoke());
            row.setKeepClean(effective.keepClean());
            row.setHometown(effective.hometown());
            row.setMajor(effective.major());
            row.setPersonality(effective.personality());
            row.setHobbies(effective.hobbies());
            row.setSelfDescription(effective.selfDescription());
            row.setDormExpectation(effective.dormExpectation());
            row.setStatus("SUBMITTED");
            row.setSubmitTime(LocalDateTime.now());

            if (existing != null) {
                answerMapper.updateById(row);
            } else {
                answerMapper.insert(row);
            }
            upserts++;
        }

        return new SeedTestAnswersReport(
                questionnaireId,
                year,
                targetCount,
                selected.size(),
                usersCreated,
                upserts,
                studentIds,
                usernames
        );
    }

    private static String fixGender(String userGender, String fallback) {
        if (userGender == null) return fallback;
        String g = userGender.trim().toUpperCase();
        if ("MALE".equals(g) || "FEMALE".equals(g)) return g;
        return fallback;
    }

    private int createSeedStudents(String year, int need, List<User> selected) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        int created = 0;
        int maleTarget = (need + 1) / 2;
        int femaleTarget = need - maleTarget;

        int maleIndex = nextSeedIndex("seed_m_");
        int femaleIndex = nextSeedIndex("seed_f_");

        while (created < need && (maleTarget > 0 || femaleTarget > 0)) {
            if (maleTarget > 0 && created < need) {
                selected.add(createOneSeedStudent(enc, "seed_m_", maleIndex++, "MALE", year));
                maleTarget--;
                created++;
            }
            if (femaleTarget > 0 && created < need) {
                selected.add(createOneSeedStudent(enc, "seed_f_", femaleIndex++, "FEMALE", year));
                femaleTarget--;
                created++;
            }
        }

        return created;
    }

    private int nextSeedIndex(String prefix) {
        List<User> list = userMapper.selectList(new LambdaQueryWrapper<User>().likeRight(User::getUsername, prefix));
        int max = 0;
        for (User u : list) {
            if (u.getUsername() == null) continue;
            String rest = u.getUsername().substring(prefix.length());
            try {
                int n = Integer.parseInt(rest);
                if (n > max) max = n;
            } catch (Exception ignored) {
            }
        }
        return max + 1;
    }

    private User createOneSeedStudent(BCryptPasswordEncoder enc,
                                     String prefix,
                                     int index,
                                     String gender,
                                     String year) {
        String suffix = String.format("%02d", index);
        String username = prefix + suffix;
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (existing != null) {
            SysUserStudent su = sysUserStudentMapper.selectById(existing.getId());
            if (su == null) {
                SysUserStudent add = new SysUserStudent();
                add.setUserId(existing.getId());
                add.setLeader(false);
                add.setAcademicYear(year);
                sysUserStudentMapper.insert(add);
            } else if (!year.equals(su.getAcademicYear())) {
                su.setAcademicYear(year);
                sysUserStudentMapper.updateById(su);
            }
            if (!UserType.STUDENT.name().equals(existing.getUserType())) {
                existing.setUserType(UserType.STUDENT.name());
                userMapper.updateById(existing);
            }
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                userMapper.updateById(existing);
            }
            if (existing.getGender() == null) {
                existing.setGender(gender);
                userMapper.updateById(existing);
            }
            return existing;
        }

        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(enc.encode("123456"));
        u.setRealName(("MALE".equals(gender) ? "压测男生" : "压测女生") + suffix);
        u.setUserNo(("MALE".equals(gender) ? "SEED-M-" : "SEED-F-") + year.replace("-", "") + "-" + suffix);
        u.setUserType(UserType.STUDENT.name());
        u.setGender(gender);
        u.setEnabled(true);
        userMapper.insert(u);

        SysUserStudent su = new SysUserStudent();
        su.setUserId(u.getId());
        su.setLeader(false);
        su.setAcademicYear(year);
        sysUserStudentMapper.insert(su);
        return u;
    }

    private record SeedProfile(String gender,
                               String wakeUpTime,
                               String sleepTime,
                               Boolean stayUpLate,
                               Boolean smoke,
                               Boolean keepClean,
                               String hometown,
                               String major,
                               String personality,
                               String hobbies,
                               String selfDescription,
                               String dormExpectation) {
        SeedProfile withGender(String g) {
            return new SeedProfile(g, wakeUpTime, sleepTime, stayUpLate, smoke, keepClean, hometown, major, personality, hobbies, selfDescription, dormExpectation);
        }
    }

    private static List<SeedProfile> defaultSeedProfiles() {
        return List.of(
                new SeedProfile("MALE", "07:00", "23:00", false, false, true, "北京", "计算机", "INTROVERT", "篮球,阅读", "作息规律，比较安静", "希望室友不抽烟，晚上安静"),
                new SeedProfile("MALE", "07:10", "23:10", false, false, true, "北京", "计算机", "INTROVERT", "篮球,阅读", "爱干净，沟通友好", "希望卫生标准统一"),
                new SeedProfile("MALE", "06:50", "22:50", false, false, true, "天津", "计算机", "AMBIVERT", "跑步,阅读", "早睡早起", "希望10点后安静"),
                new SeedProfile("MALE", "10:30", "03:00", true, true, false, "广东", "艺术", "EXTROVERT", "游戏,聚会", "夜猫子", "希望晚上热闹一些"),
                new SeedProfile("MALE", "11:00", "04:00", true, true, false, "广东", "艺术", "EXTROVERT", "游戏,聚会", "熬夜党", "不太在意卫生"),
                new SeedProfile("FEMALE", "07:20", "23:20", false, false, true, "天津", "软件工程", "AMBIVERT", "音乐,羽毛球", "好沟通，作息稳定", "希望大家能协商作息"),
                new SeedProfile("FEMALE", "07:30", "23:30", false, false, true, "天津", "软件工程", "AMBIVERT", "音乐,羽毛球", "性格随和", "希望宿舍氛围轻松"),
                new SeedProfile("FEMALE", "08:30", "00:30", true, false, true, "河北", "金融", "EXTROVERT", "追剧,美妆", "喜欢社交", "希望不要太早关灯"),
                new SeedProfile("FEMALE", "08:20", "00:20", true, false, true, "河北", "金融", "EXTROVERT", "追剧,美妆", "容易相处", "希望不要太早关灯"),
                new SeedProfile("MALE", "07:40", "23:40", false, false, true, "山东", "土木", "AMBIVERT", "健身,篮球", "不抽烟，爱运动", "希望室友作息接近"),
                new SeedProfile("FEMALE", "07:50", "23:50", false, false, true, "山东", "临床", "INTROVERT", "阅读,手工", "比较安静", "希望晚上少吵闹")
        );
    }
}
