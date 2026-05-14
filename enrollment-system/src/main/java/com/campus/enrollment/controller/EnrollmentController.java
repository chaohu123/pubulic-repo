package com.campus.enrollment.controller;

import com.campus.enrollment.common.ApiResponse;
import com.campus.enrollment.dto.CsvProcessRequest;
import com.campus.enrollment.dto.ExportRequest;
import com.campus.enrollment.dto.MultiSearchRequest;
import com.campus.enrollment.dto.ProcessResult;
import com.campus.enrollment.dto.SearchRequest;
import com.campus.enrollment.dto.SearchResult;
import com.campus.enrollment.dto.SortRequest;
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
import org.springframework.web.bind.annotation.RestController;

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
     * 单关键词维度检索。
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<SearchResult>> search(@Valid @RequestBody SearchRequest request) {
        SearchResult out = enrollmentService.search(request);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /**
     * 多条件组合检索（AND）。
     */
    @PostMapping("/search-multi")
    public ResponseEntity<ApiResponse<SearchResult>> searchMulti(@Valid @RequestBody MultiSearchRequest request) {
        SearchResult out = enrollmentService.searchMulti(request);
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
