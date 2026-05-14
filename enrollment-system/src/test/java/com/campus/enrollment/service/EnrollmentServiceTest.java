package com.campus.enrollment.service;

import com.campus.enrollment.dto.CsvProcessRequest;
import com.campus.enrollment.dto.ProcessResult;
import com.campus.enrollment.dto.SearchResult;
import com.campus.enrollment.dto.SortField;
import com.campus.enrollment.dto.SortRequest;
import com.campus.enrollment.dto.UnifiedSearchRequest;
import com.campus.enrollment.entity.EnrollRecord;
import com.campus.enrollment.exception.BusinessException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 选课服务单元测试（不启动 Spring 容器）：覆盖解析、去重、排序、分类、检索与性能。
 */
class EnrollmentServiceTest {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentServiceTest.class);

    private final EnrollmentService service = new EnrollmentService();

    /**
     * CSV 解析：应正确统计合法行、空行、非法行，并忽略无法解析的行。
     */
    @Test
    void csvParse_countsEmptyAndInvalidLines() {
        String csv = """
                S000001,C000001,有效1,专业课

                S000002,C000002,有效2,公共课
                BAD,C000003,非法学号,公共课
                S000003,C000003,,专业课
                只有一列
                """;
        CsvProcessRequest req = new CsvProcessRequest();
        req.setCsv(csv);
        ProcessResult r = service.processCsv(req);
        log.info("【CSV解析测试】raw={} empty={} invalid={} valid={} output={}",
                r.getRawLineCount(), r.getEmptyLineCount(), r.getInvalidLineCount(), r.getParsedValidCount(), r.getOutputCount());
        assertTrue(r.getEmptyLineCount() >= 1);
        assertTrue(r.getInvalidLineCount() >= 1);
        assertEquals(2, r.getParsedValidCount());
        assertEquals(2, r.getOutputCount());
    }

    /**
     * 数据去重：同一学生同一课程仅保留首次出现的课程名称。
     */
    @Test
    void dedupe_keepsFirstOccurrence() {
        String csv = """
                S000002,C000001,B,专业课
                S000001,C000002,A,公共课
                S000001,C000001,A,专业课
                S000001,C000001,A重复,公共课
                """;
        CsvProcessRequest req = new CsvProcessRequest();
        req.setCsv(csv);
        ProcessResult r = service.processCsv(req);
        log.info("【去重测试】解析={} 输出={} 合并重复={}", r.getParsedValidCount(), r.getOutputCount(), r.getDuplicateMergedCount());
        assertEquals(4, r.getParsedValidCount());
        assertEquals(3, r.getOutputCount());
        assertEquals(1, r.getDuplicateMergedCount());
        assertEquals("S000001", r.getRecords().get(0).getStudentId());
        assertEquals("C000001", r.getRecords().get(0).getCourseId());
        assertEquals("A", r.getRecords().get(0).getCourseName());
    }

    /**
     * 数据排序：默认按学生 ID 升序，相同时按课程 ID 升序。
     */
    @Test
    void sort_defaultComparator() {
        String csv = """
                S000003,C000001,X,选修课
                S000001,C000002,Y,专业课
                S000001,C000001,Z,公共课
                """;
        ProcessResult r = service.processCsv(csvRequest(csv));
        log.info("【排序测试】首条学号={} 课号={}", r.getRecords().get(0).getStudentId(), r.getRecords().get(0).getCourseId());
        assertEquals("S000001", r.getRecords().get(0).getStudentId());
        assertEquals("C000001", r.getRecords().get(0).getCourseId());
        assertEquals("S000003", r.getRecords().get(2).getStudentId());
    }

    /**
     * 自动分类：无第四列时根据课程名推断类型。
     */
    @Test
    void autoClassify_inferFromCourseName() {
        String csv = "S000001,C000001,大学英语,\nS000001,C000002,Java程序设计,\n";
        ProcessResult r = service.processCsv(csvRequest(csv));
        log.info("【自动分类测试】记录1类型={} 记录2类型={}",
                r.getRecords().get(0).getCourseType(), r.getRecords().get(1).getCourseType());
        assertEquals(EnrollmentService.TYPE_PUBLIC, r.getRecords().get(0).getCourseType());
        assertEquals(EnrollmentService.TYPE_MAJOR, r.getRecords().get(1).getCourseType());
    }

    /**
     * 统一检索-快捷维度：无匹配时返回固定提示文案。
     */
    @Test
    void searchNoMatch() {
        UnifiedSearchRequest req = new UnifiedSearchRequest();
        req.setRecords(List.of(new EnrollRecord("S000001", "C000001", "X", EnrollmentService.TYPE_MAJOR)));
        req.setQuickDimension(EnrollmentService.SEARCH_COURSE_NAME);
        req.setQuickKeyword("不存在");
        SearchResult out = service.searchUnified(req);
        log.info("【检索测试】matched={} message={}", out.isMatched(), out.getMessage());
        assertFalse(out.isMatched());
        assertEquals(EnrollmentService.NO_MATCH_MESSAGE, out.getMessage());
    }

    /**
     * 统一检索：无任何条件时返回全量。
     */
    @Test
    void searchEmptyKeywordReturnsAll() {
        UnifiedSearchRequest req = new UnifiedSearchRequest();
        req.setRecords(List.of(new EnrollRecord("S000001", "C000001", "X", EnrollmentService.TYPE_MAJOR)));
        SearchResult out = service.searchUnified(req);
        log.info("【检索无条件】条数={} 耗时={}ms", out.getRecords().size(), out.getElapsedMs());
        assertTrue(out.isMatched());
        assertEquals(1, out.getRecords().size());
    }

    /**
     * 非法快捷维度：应抛出业务异常。
     */
    @Test
    void searchInvalidType_throws() {
        UnifiedSearchRequest req = new UnifiedSearchRequest();
        req.setRecords(List.of(new EnrollRecord("S000001", "C000001", "X", EnrollmentService.TYPE_MAJOR)));
        req.setQuickDimension("BAD_TYPE");
        req.setQuickKeyword("a");
        assertThrows(BusinessException.class, () -> service.searchUnified(req));
    }

    /**
     * 统一检索：学生 ID 片段 + 课程类型同时满足（多列 AND）。
     */
    @Test
    void multiSearch_andLogic() {
        List<EnrollRecord> list = List.of(
                new EnrollRecord("S000001", "C000001", "A", EnrollmentService.TYPE_MAJOR),
                new EnrollRecord("S000002", "C000002", "B", EnrollmentService.TYPE_PUBLIC),
                new EnrollRecord("S000001", "C000003", "C", EnrollmentService.TYPE_PUBLIC)
        );
        UnifiedSearchRequest req = new UnifiedSearchRequest();
        req.setRecords(list);
        req.setStudentId("S000001");
        req.setCourseType(EnrollmentService.TYPE_PUBLIC);
        SearchResult out = service.searchUnified(req);
        log.info("【组合检索】命中条数={} 耗时={}ms", out.getRecords().size(), out.getElapsedMs());
        assertTrue(out.isMatched());
        assertEquals(1, out.getRecords().size());
        assertEquals("C000003", out.getRecords().get(0).getCourseId());
    }

    /**
     * 统一检索：多列 AND + 快捷维度关键词同时生效。
     */
    @Test
    void unifiedSearch_columnsAndQuick_and() {
        List<EnrollRecord> list = List.of(
                new EnrollRecord("S000001", "C000001", "高等数学", EnrollmentService.TYPE_PUBLIC),
                new EnrollRecord("S000001", "C000002", "Java程序设计", EnrollmentService.TYPE_MAJOR)
        );
        UnifiedSearchRequest req = new UnifiedSearchRequest();
        req.setRecords(list);
        req.setStudentId("S000001");
        req.setQuickDimension(EnrollmentService.SEARCH_COURSE_NAME);
        req.setQuickKeyword("Java");
        SearchResult out = service.searchUnified(req);
        assertTrue(out.isMatched());
        assertEquals(1, out.getRecords().size());
        assertEquals("C000002", out.getRecords().get(0).getCourseId());
    }

    /**
     * Excel 导入：含表头行时应正确解析数据行。
     */
    @Test
    void processExcel_withHeader_ok() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("选课");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("学生ID");
            h.createCell(1).setCellValue("课程ID");
            h.createCell(2).setCellValue("课程名称");
            h.createCell(3).setCellValue("课程类型");
            Row d = sh.createRow(1);
            d.createCell(0).setCellValue("S000099");
            d.createCell(1).setCellValue("C000099");
            d.createCell(2).setCellValue("软件工程");
            d.createCell(3).setCellValue("专业课");
            wb.write(bos);
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bos.toByteArray());
        ProcessResult r = service.processExcel(file);
        log.info("【Excel导入】输出={}", r.getOutputCount());
        assertEquals(1, r.getOutputCount());
        assertEquals("S000099", r.getRecords().get(0).getStudentId());
        assertEquals("软件工程", r.getRecords().get(0).getCourseName());
    }

    /**
     * 排序切换：按课程名称降序。
     */
    @Test
    void sortByCourseName_desc() {
        SortRequest req = new SortRequest();
        req.setRecords(List.of(
                new EnrollRecord("S000001", "C000001", "Alpha", EnrollmentService.TYPE_MAJOR),
                new EnrollRecord("S000002", "C000002", "Zeta", EnrollmentService.TYPE_PUBLIC)
        ));
        req.setSortField(SortField.COURSE_NAME);
        req.setAscending(false);
        List<EnrollRecord> sorted = service.sortRecords(req);
        log.info("【排序切换】首条课名={}", sorted.get(0).getCourseName());
        assertEquals("Zeta", sorted.get(0).getCourseName());
    }

    /**
     * 大数据量：约 1000 条合法唯一记录 + 噪声，处理与组合检索应在 1 秒内完成。
     */
    @Test
    void performance_thousandRows_processAndSearch_underOneSecond() {
        String csv = service.generateMixedTestCsv(1000);
        CsvProcessRequest req = new CsvProcessRequest();
        req.setCsv(csv);
        long t0 = System.nanoTime();
        ProcessResult r = service.processCsv(req);
        long processMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("【性能-处理】输出={} 耗时={}ms 服务端统计={}ms", r.getOutputCount(), processMs, r.getElapsedMs());
        assertEquals(1000, r.getOutputCount());
        assertTrue(processMs <= 1000, "处理应 ≤1s，实际 " + processMs + "ms");

        UnifiedSearchRequest ms = new UnifiedSearchRequest();
        ms.setRecords(r.getRecords());
        ms.setCourseType(EnrollmentService.TYPE_MAJOR);
        long t1 = System.nanoTime();
        SearchResult s = service.searchUnified(ms);
        long searchMs = (System.nanoTime() - t1) / 1_000_000L;
        log.info("【性能-组合检索】命中={} 耗时={}ms 服务端统计={}ms", s.getRecords().size(), searchMs, s.getElapsedMs());
        assertTrue(s.isMatched());
        assertTrue(searchMs <= 1000, "检索应 ≤1s，实际 " + searchMs + "ms");
    }

    /**
     * 异常数据：测试数据规模非法时抛出业务异常。
     */
    @Test
    void invalidMockSize_throws() {
        assertThrows(BusinessException.class, () -> service.generateMixedTestCsv(123));
    }

    private static CsvProcessRequest csvRequest(String csv) {
        CsvProcessRequest req = new CsvProcessRequest();
        req.setCsv(csv);
        return req;
    }
}
