package com.smartdorm.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.GenderUtil;
import com.smartdorm.backend.common.UserType;
import com.smartdorm.backend.dto.CreateUserRequest;
import com.smartdorm.backend.entity.SysUserAdmin;
import com.smartdorm.backend.entity.SysUserDormManager;
import com.smartdorm.backend.entity.SysUserStudent;
import com.smartdorm.backend.entity.User;
import com.smartdorm.backend.entity.AssignmentResult;
import com.smartdorm.backend.entity.DormManagerScope;
import com.smartdorm.backend.entity.HygieneCheckin;
import com.smartdorm.backend.entity.HygieneDutyTask;
import com.smartdorm.backend.entity.NotifyInbox;
import com.smartdorm.backend.entity.QuestionnaireAnswer;
import com.smartdorm.backend.mapper.SysUserAdminMapper;
import com.smartdorm.backend.mapper.SysUserDormManagerMapper;
import com.smartdorm.backend.mapper.SysUserStudentMapper;
import com.smartdorm.backend.mapper.UserMapper;
import com.smartdorm.backend.mapper.AssignmentResultMapper;
import com.smartdorm.backend.mapper.DormManagerScopeMapper;
import com.smartdorm.backend.mapper.HygieneCheckinMapper;
import com.smartdorm.backend.mapper.HygieneDutyTaskMapper;
import com.smartdorm.backend.mapper.NotifyInboxMapper;
import com.smartdorm.backend.mapper.QuestionnaireAnswerMapper;
import com.smartdorm.backend.vo.UserCreateResponse;
import com.smartdorm.backend.vo.UserListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final SysUserAdminMapper sysUserAdminMapper;
    private final SysUserStudentMapper sysUserStudentMapper;
    private final SysUserDormManagerMapper sysUserDormManagerMapper;
    private final PasswordEncoder passwordEncoder;
    private final QuestionnaireAnswerMapper questionnaireAnswerMapper;
    private final AssignmentResultMapper assignmentResultMapper;
    private final DormManagerScopeMapper dormManagerScopeMapper;
    private final HygieneDutyTaskMapper hygieneDutyTaskMapper;
    private final HygieneCheckinMapper hygieneCheckinMapper;
    private final NotifyInboxMapper notifyInboxMapper;

    public List<UserListItemResponse> listUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId));
        Set<Long> studentIds = users.stream()
                .filter(u -> UserType.STUDENT.name().equals(u.getUserType()))
                .map(User::getId)
                .collect(Collectors.toSet());
        Map<Long, SysUserStudent> studentById = studentIds.isEmpty()
                ? Map.of()
                : sysUserStudentMapper.selectList(
                        new LambdaQueryWrapper<SysUserStudent>().in(SysUserStudent::getUserId, studentIds))
                .stream()
                .collect(Collectors.toMap(SysUserStudent::getUserId, s -> s, (a, b) -> a));

        return users.stream()
                .map(user -> {
                    SysUserStudent stu = studentById.get(user.getId());
                    boolean leader = stu != null && Boolean.TRUE.equals(stu.getLeader());
                    String academicYear = stu != null ? stu.getAcademicYear() : null;
                    return new UserListItemResponse(
                            user.getId(),
                            user.getUsername(),
                            user.getRealName(),
                            user.getUserNo(),
                            user.getUserType(),
                            user.getGender(),
                            leader,
                            academicYear,
                            user.getEnabled());
                })
                .toList();
    }

    @Transactional
    public UserCreateResponse createUser(CreateUserRequest request) {
        return createUserInternal(request, true);
    }

    @Transactional
    public int importUsersCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择 CSV 文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(400, "读取 CSV 失败");
        }
        String text = decodeCsvText(bytes);
        text = stripBom(text);
        List<String> lines = splitLines(text);
        if (lines.isEmpty()) {
            throw new BusinessException(400, "CSV 内容为空");
        }

        List<String> header = parseCsvLine(lines.get(0));
        if (header.isEmpty()) {
            throw new BusinessException(400, "CSV 表头为空");
        }

        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = normalizeHeader(header.get(i));
            if (!key.isEmpty()) {
                colIndex.put(key, i);
            }
        }

        requireColumns(colIndex, Set.of("username", "realname", "usertype", "gender"));

        Set<String> usernameInFile = new HashSet<>();
        Set<String> userNoInFile = new HashSet<>();
        int imported = 0;

        for (int i = 1; i < lines.size(); i++) {
            int rowNo = i + 1;
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            List<String> cells = parseCsvLine(line);
            if (isAllBlank(cells)) {
                continue;
            }

            String username = getCell(cells, colIndex, "username").trim();
            String password = getCell(cells, colIndex, "password").trim();
            String realName = getCell(cells, colIndex, "realname").trim();
            String userNo = getCell(cells, colIndex, "userno").trim();
            String userType = getCell(cells, colIndex, "usertype").trim();
            String gender = getCell(cells, colIndex, "gender").trim();
            String leaderRaw = getCell(cells, colIndex, "leader").trim();
            String academicYear = getCell(cells, colIndex, "academicyear").trim();
            String enabledRaw = getCell(cells, colIndex, "enabled").trim();

            if (username.isEmpty()) {
                throw new BusinessException(400, "第 " + rowNo + " 行：username 不能为空");
            }
            String u0 = username.trim().toUpperCase();
            if (u0.startsWith("__EXAMPLE__") || u0.startsWith("EXAMPLE_")) {
                continue;
            }
            String usernameKey = username.toLowerCase();
            if (!usernameInFile.add(usernameKey)) {
                throw new BusinessException(400, "第 " + rowNo + " 行：username 在文件内重复：" + username);
            }
            if (!userNo.isEmpty()) {
                if (!userNoInFile.add(userNo)) {
                    throw new BusinessException(400, "第 " + rowNo + " 行：userNo 在文件内重复：" + userNo);
                }
            } else {
                userNo = null;
            }

            if (password.isEmpty()) {
                password = "123456";
            }

            Boolean leader = parseBooleanNullable(leaderRaw);
            Boolean enabled = parseBooleanNullable(enabledRaw);

            CreateUserRequest req = new CreateUserRequest();
            req.setUsername(username);
            req.setPassword(password);
            req.setRealName(realName);
            req.setUserNo(userNo);
            req.setUserType(userType);
            req.setGender(gender);
            req.setLeader(Boolean.TRUE.equals(leader));
            req.setAcademicYear(academicYear.isEmpty() ? null : academicYear);

            try {
                createUserInternal(req, enabled == null ? Boolean.TRUE : enabled);
                imported++;
            } catch (BusinessException ex) {
                throw new BusinessException(ex.getCode(), "第 " + rowNo + " 行：" + ex.getMessage());
            }
        }

        if (imported == 0) {
            throw new BusinessException(400, "未导入任何数据（请检查 CSV 是否只有表头或空行）");
        }
        return imported;
    }

    private static String decodeCsvText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";

        // Excel 在 Windows 下常见保存为 GBK；优先严格按 UTF-8 解码，失败或出现明显替换字符再退回 GBK。
        String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null && !utf8.contains("\uFFFD")) {
            return utf8;
        }
        Charset gbk = Charset.forName("GBK");
        String gbkText = decodeStrict(bytes, gbk);
        if (gbkText != null) {
            return gbkText;
        }
        // 兜底：UTF-8 宽松解码
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer cb = decoder.decode(ByteBuffer.wrap(bytes));
            return cb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void deleteUser(Long userId, boolean force) {
        if (userId == null) {
            throw new BusinessException(400, "userId 不能为空");
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if ("admin".equalsIgnoreCase(u.getUsername())) {
            throw new BusinessException(400, "禁止删除系统初始管理员账号");
        }

        if (!force) {
            long refs = 0;
            refs += countRefsQuestionnaireAnswer(userId);
            refs += countRefsAssignmentResult(userId);
            refs += countRefsDormManagerScope(userId);
            refs += countRefsHygiene(userId);
            refs += countRefsNotify(userId);
            if (refs > 0) {
                throw new BusinessException(400, "该用户已产生业务数据，已阻止删除（可传 force=true 强制删除）");
            }
        }

        userMapper.deleteById(userId);
    }

    private long countRefsQuestionnaireAnswer(Long userId) {
        Long c = questionnaireAnswerMapper.selectCount(
                new LambdaQueryWrapper<QuestionnaireAnswer>().eq(QuestionnaireAnswer::getStudentId, userId));
        return c == null ? 0 : c;
    }

    private long countRefsAssignmentResult(Long userId) {
        Long c = assignmentResultMapper.selectCount(
                new LambdaQueryWrapper<AssignmentResult>().eq(AssignmentResult::getStudentId, userId));
        return c == null ? 0 : c;
    }

    private long countRefsDormManagerScope(Long userId) {
        Long c = dormManagerScopeMapper.selectCount(
                new LambdaQueryWrapper<DormManagerScope>().eq(DormManagerScope::getManagerId, userId));
        return c == null ? 0 : c;
    }

    private long countRefsHygiene(Long userId) {
        long total = 0;
        Long c1 = hygieneDutyTaskMapper.selectCount(
                new LambdaQueryWrapper<HygieneDutyTask>().eq(HygieneDutyTask::getDutyUserId, userId));
        Long c2 = hygieneCheckinMapper.selectCount(
                new LambdaQueryWrapper<HygieneCheckin>().eq(HygieneCheckin::getUserId, userId));
        total += c1 == null ? 0 : c1;
        total += c2 == null ? 0 : c2;
        return total;
    }

    private long countRefsNotify(Long userId) {
        Long c = notifyInboxMapper.selectCount(
                new LambdaQueryWrapper<NotifyInbox>().eq(NotifyInbox::getRecipientUserId, userId));
        return c == null ? 0 : c;
    }

    private UserCreateResponse createUserInternal(CreateUserRequest request, boolean enabled) {
        if (request == null) {
            throw new BusinessException(400, "请求不能为空");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "初始密码不能为空");
        }
        if (request.getRealName() == null || request.getRealName().isBlank()) {
            throw new BusinessException(400, "姓名不能为空");
        }
        if (request.getUserType() == null || request.getUserType().isBlank()) {
            throw new BusinessException(400, "角色类型不能为空");
        }
        if (request.getGender() == null || request.getGender().isBlank()) {
            throw new BusinessException(400, "性别不能为空");
        }

        UserType userType = UserType.fromCode(request.getUserType());

        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (usernameCount != null && usernameCount > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        if (request.getUserNo() != null && !request.getUserNo().isBlank()) {
            Long userNoCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getUserNo, request.getUserNo())
            );
            if (userNoCount != null && userNoCount > 0) {
                throw new BusinessException(400, "编号已存在");
            }
        }

        boolean isLeader = userType == UserType.STUDENT && Boolean.TRUE.equals(request.getLeader());
        if (userType != UserType.STUDENT && Boolean.TRUE.equals(request.getLeader())) {
            throw new BusinessException(400, "只有学生账号才能设置为宿舍长");
        }

        String academicYear = null;
        if (userType == UserType.STUDENT) {
            if (request.getAcademicYear() == null || request.getAcademicYear().isBlank()) {
                throw new BusinessException(400, "学生账号必须填写学年");
            }
            academicYear = request.getAcademicYear().trim();
        } else if (request.getAcademicYear() != null && !request.getAcademicYear().isBlank()) {
            throw new BusinessException(400, "学年仅对学生账号有效");
        }

        String gender = GenderUtil.requireCode(request.getGender());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setUserNo(request.getUserNo());
        user.setUserType(userType.name());
        user.setGender(gender);
        user.setEnabled(enabled);
        userMapper.insert(user);

        Long id = Objects.requireNonNull(user.getId(), "user id");
        switch (userType) {
            case SYS_ADMIN -> {
                SysUserAdmin row = new SysUserAdmin();
                row.setUserId(id);
                sysUserAdminMapper.insert(row);
            }
            case STUDENT -> {
                SysUserStudent row = new SysUserStudent();
                row.setUserId(id);
                row.setLeader(isLeader);
                row.setAcademicYear(academicYear);
                sysUserStudentMapper.insert(row);
            }
            case DORM_MANAGER -> {
                SysUserDormManager row = new SysUserDormManager();
                row.setUserId(id);
                sysUserDormManagerMapper.insert(row);
            }
        }

        User persisted = userMapper.selectById(user.getId());
        if (persisted == null) {
            throw new BusinessException(500, "创建用户后读取数据失败");
        }
        if (!gender.equals(persisted.getGender())) {
            throw new BusinessException(500,
                    "性别未能正确写入数据库（期望 " + gender + "，实际 " + persisted.getGender()
                            + "）。请确认数据库 sys_user.gender 列可写。");
        }

        boolean leaderOut = false;
        String yearOut = null;
        if (userType == UserType.STUDENT) {
            SysUserStudent stu = sysUserStudentMapper.selectById(id);
            leaderOut = stu != null && Boolean.TRUE.equals(stu.getLeader());
            yearOut = stu != null ? stu.getAcademicYear() : null;
        }

        return new UserCreateResponse(
                persisted.getId(),
                persisted.getUsername(),
                persisted.getRealName(),
                persisted.getUserNo(),
                persisted.getUserType(),
                persisted.getGender(),
                leaderOut,
                yearOut,
                persisted.getEnabled()
        );
    }

    private static void requireColumns(Map<String, Integer> colIndex, Set<String> required) {
        List<String> missing = required.stream().filter(k -> !colIndex.containsKey(k)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(400, "CSV 表头缺少字段：" + String.join(", ", missing));
        }
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        return s.replaceAll("\\s+", "").toLowerCase();
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) return List.of();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] arr = normalized.split("\n", -1);
        List<String> out = new ArrayList<>(arr.length);
        for (String s : arr) out.add(s == null ? "" : s);
        return out;
    }

    private static String stripBom(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }

    private static boolean isAllBlank(List<String> cells) {
        if (cells == null || cells.isEmpty()) return true;
        for (String c : cells) {
            if (c != null && !c.trim().isEmpty()) return false;
        }
        return true;
    }

    private static String getCell(List<String> cells, Map<String, Integer> colIndex, String key) {
        Integer idx = colIndex.get(key);
        if (idx == null) return "";
        if (idx < 0 || idx >= cells.size()) return "";
        String v = cells.get(idx);
        return v == null ? "" : v;
    }

    private static Boolean parseBooleanNullable(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String u = s.toUpperCase();
        if ("1".equals(u) || "TRUE".equals(u) || "YES".equals(u) || "Y".equals(u)) return true;
        if ("0".equals(u) || "FALSE".equals(u) || "NO".equals(u) || "N".equals(u)) return false;
        throw new BusinessException(400, "布尔字段仅支持 true/false/1/0");
    }

    private static List<String> parseCsvLine(String line) {
        if (line == null) return List.of();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else if (ch == '"') {
                    inQuotes = true;
                } else {
                    cur.append(ch);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }
}
