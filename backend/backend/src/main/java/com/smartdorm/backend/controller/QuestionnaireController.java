package com.smartdorm.backend.controller;

import com.smartdorm.backend.common.Result;
import com.smartdorm.backend.dto.CreateQuestionnaireRequest;
import com.smartdorm.backend.dto.SubmitAnswerRequest;
import com.smartdorm.backend.entity.QuestionnaireAnswer;
import com.smartdorm.backend.service.QuestionnaireService;
import com.smartdorm.backend.vo.QuestionnaireVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/questionnaire")
@RequiredArgsConstructor
@CrossOrigin
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;

    /** 管理员：查询所有问卷（含回收统计） */
    @GetMapping
    public Result<List<QuestionnaireVO>> list() {
        return Result.success(questionnaireService.listQuestionnaires());
    }

    /** 管理员：创建问卷 */
    @PostMapping
    public Result<QuestionnaireVO> create(@Valid @RequestBody CreateQuestionnaireRequest request,
                                          @RequestParam Long adminId) {
        return Result.success(questionnaireService.createQuestionnaire(request, adminId));
    }

    /** 管理员：发布问卷 */
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        questionnaireService.publishQuestionnaire(id);
        return Result.success();
    }

    /** 管理员：关闭问卷 */
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        questionnaireService.closeQuestionnaire(id);
        return Result.success();
    }

    /** 管理员：删除问卷（逻辑删除，答卷数据保留） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionnaireService.deleteQuestionnaire(id);
        return Result.success();
    }

    /** 管理员：查看某问卷所有答卷 */
    @GetMapping("/{id}/answers")
    public Result<List<QuestionnaireAnswer>> listAnswers(@PathVariable Long id) {
        return Result.success(questionnaireService.listAnswers(id));
    }

    /** 管理员：导出答卷 CSV */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportAnswers(@PathVariable Long id) {
        byte[] body = questionnaireService.exportAnswersCsv(id);
        String filename = URLEncoder.encode("questionnaire_" + id + "_answers.csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /** 管理员：自动注入测试答卷（跳过逐个填问卷） */
    @PostMapping("/{id}/seed-test-answers")
    public Result<QuestionnaireService.SeedTestAnswersReport> seedTestAnswers(@PathVariable Long id,
                                                                              @RequestParam(required = false) String academicYear,
                                                                              @RequestParam(required = false) Integer count,
                                                                              @RequestParam(defaultValue = "true") boolean createStudentsIfNeeded) {
        return Result.success(questionnaireService.seedTestAnswers(id, academicYear, count, createStudentsIfNeeded));
    }

    /** 学生：提交/更新答卷 */
    @PostMapping("/answer")
    public Result<Void> submitAnswer(@Valid @RequestBody SubmitAnswerRequest request) {
        questionnaireService.submitAnswer(request);
        return Result.success();
    }

    /** 学生：查询自己的答卷 */
    @GetMapping("/answer")
    public Result<QuestionnaireAnswer> getMyAnswer(@RequestParam Long questionnaireId,
                                                   @RequestParam Long studentId) {
        return Result.success(questionnaireService.getMyAnswer(questionnaireId, studentId));
    }
}
