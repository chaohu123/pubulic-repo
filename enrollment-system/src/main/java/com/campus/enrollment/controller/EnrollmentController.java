package com.campus.enrollment.controller;

import com.campus.enrollment.common.ApiResponse;
import com.campus.enrollment.dto.CsvProcessRequest;
import com.campus.enrollment.dto.ExportRequest;
import com.campus.enrollment.dto.ProcessResult;
import com.campus.enrollment.dto.SearchResult;
import com.campus.enrollment.dto.SortRequest;
import com.campus.enrollment.dto.UnifiedSearchRequest;
import com.campus.enrollment.entity.EnrollRecord;
import com.campus.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 选课 REST 接口：仅做参数接收与转发，业务均在 {@link EnrollmentService}。
 */
@RestController
@RequestMapping("/api/enrollment")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * CSV 批量导入并处理。
     */
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<ProcessResult>> process(@Valid @RequestBody CsvProcessRequest request) {
        ProcessResult result = enrollmentService.processCsv(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Excel（.xlsx）批量导入：首表 A-D 列与 CSV 字段一致，处理后与 CSV 管道相同。
     */
    @PostMapping(value = "/process-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProcessResult>> processExcel(@RequestPart("file") MultipartFile file) {
        ProcessResult result = enrollmentService.processExcel(file);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 统一检索：多列 AND + 可选快捷维度关键词（与多列同时生效时为 AND）。
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<SearchResult>> search(@Valid @RequestBody UnifiedSearchRequest request) {
        SearchResult out = enrollmentService.searchUnified(request);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /**
     * 列表排序切换。
     */
    @PostMapping("/sort")
    public ResponseEntity<ApiResponse<List<EnrollRecord>>> sort(@Valid @RequestBody SortRequest request) {
        List<EnrollRecord> sorted = enrollmentService.sortRecords(request);
        return ResponseEntity.ok(ApiResponse.ok(sorted));
    }

    /**
     * 导出当前数据为 CSV 文件下载。
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        String csv = enrollmentService.buildExportCsv(request);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enrollments.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * 生成含噪声的测试用 CSV 文本（纯文本响应，便于粘贴到文本框）。
     */
    @GetMapping(value = "/mock-csv", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> mockCsv(@RequestParam(defaultValue = "100") int size) {
        String csv = enrollmentService.generateMixedTestCsv(size);
        return ResponseEntity.ok(csv);
    }

    /**
     * 内置样例数据（JSON）。
     */
    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<List<EnrollRecord>>> sample() {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.sampleRecords()));
    }
}
