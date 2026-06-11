package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.CreateUserRequest;
import com.smartdorm.backend.service.AdminUserService;
import com.smartdorm.backend.vo.UserCreateResponse;
import com.smartdorm.backend.vo.UserListItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@CrossOrigin
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<List<UserListItemResponse>> listUsers() {
        return Result.success(adminUserService.listUsers());
    }

    @PostMapping
    public Result<UserCreateResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(adminUserService.createUser(request));
    }

    @PostMapping("/import")
    public Result<String> importUsers(@RequestPart("file") MultipartFile file) {
        int count = adminUserService.importUsersCsv(file);
        return Result.success("批量导入成功，共导入 " + count + " 条");
    }

    @GetMapping(value = "/import/template", produces = "text/csv")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        String template = String.join("\n",
                "username,password,realName,userNo,userType,gender,leader,academicYear,enabled",
                "# 示例行会被系统自动忽略（username 以 __EXAMPLE__ 或 EXAMPLE_ 开头），可不删除；需要导入时去掉前缀并改成真实数据",
                "__EXAMPLE__stu_20260001,123456,张三,20260001,STUDENT,MALE,false,2025-2026,true",
                "__EXAMPLE__stu_20260002,,李四,20260002,STUDENT,FEMALE,true,2025-2026,true",
                "__EXAMPLE__dorm_mgr_01,123456,王宿管,DM-01,DORM_MANAGER,MALE,,,",
                "__EXAMPLE__admin_01,admin123456,赵管理员,SYS-01,SYS_ADMIN,FEMALE,,,"
        ) + "\n";

        // 带 UTF-8 BOM，保证 Windows Excel 打开中文不乱码
        byte[] content = template.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[content.length + 3];
        body[0] = (byte) 0xEF;
        body[1] = (byte) 0xBB;
        body[2] = (byte) 0xBF;
        System.arraycopy(content, 0, body, 3, content.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''user_import_template.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id, @RequestParam(required = false) Boolean force) {
        adminUserService.deleteUser(id, Boolean.TRUE.equals(force));
        return Result.success("删除成功");
    }
}
