package com.campus.enrollment.service;

import com.campus.enrollment.dto.CsvProcessRequest;
import com.campus.enrollment.dto.ExportRequest;
import com.campus.enrollment.dto.ProcessResult;
import com.campus.enrollment.dto.SearchResult;
import com.campus.enrollment.dto.SortField;
import com.campus.enrollment.dto.SortRequest;
import com.campus.enrollment.dto.UnifiedSearchRequest;
import com.campus.enrollment.entity.EnrollRecord;
import com.campus.enrollment.exception.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 选课处理核心业务：CSV 解析、分类、去重、排序、检索、导出、测试数据生成。
 * <p>
 * 性能要点：解析单行一次遍历；去重使用 {@link HashSet} 存复合键 O(1)；排序一次；检索单次 Stream 过滤链。
 */
@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    public static final String TYPE_PUBLIC = "公共课";
    public static final String TYPE_MAJOR = "专业课";
    public static final String TYPE_ELECTIVE = "选修课";
    public static final String NO_MATCH_MESSAGE = "无匹配选课记录";

    public static final String SEARCH_STUDENT_ID = "STUDENT_ID";
    public static final String SEARCH_COURSE_ID = "COURSE_ID";
    public static final String SEARCH_COURSE_NAME = "COURSE_NAME";
    public static final String SEARCH_COURSE_TYPE = "COURSE_TYPE";

    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^S\\d{6}$");
    private static final Pattern COURSE_ID_PATTERN = Pattern.compile("^C\\d{6}$");
    private static final int MAX_WARNINGS = 80;

    /** 单关键词检索允许的 searchType 取值 */
    private static final Set<String> ALLOWED_SEARCH = Set.of(
            SEARCH_STUDENT_ID, SEARCH_COURSE_ID, SEARCH_COURSE_NAME, SEARCH_COURSE_TYPE);

    private static final String[] COURSE_NAME_POOL = {
            "Java程序设计", "数据结构", "计算机网络", "高等数学", "Python开发",
            "软件工程", "大学英语", "思政基础", "操作系统", "数据库系统"
    };

    /**
     * 处理 CSV：单行解析统计 + 分类 + HashSet 复合键去重（保留首次）+ 排序 + 类型统计。
     */
    public ProcessResult processCsv(CsvProcessRequest request) {
        long start = System.nanoTime();
        ProcessResult result = new ProcessResult();
        String raw = request.getCsv() == null ? "" : request.getCsv();

        ParseOutcome parseOutcome = parseCsvLinesDetailed(raw);
        result.setRawLineCount(parseOutcome.rawLineCount());
        result.setEmptyLineCount(parseOutcome.emptyLineCount());
        result.setInvalidLineCount(parseOutcome.invalidLineCount());
        result.setWarnings(new ArrayList<>(parseOutcome.warnings()));
        List<EnrollRecord> parsed = parseOutcome.records();

        // 单次流：归一化课程类型（避免二次循环可合并进下方 for，为可读性保留）
        parsed.forEach(this::applyCourseType);

        // HashSet 记录复合键，一次循环完成去重（保留文件内首次出现的记录）
        Set<String> seenKeys = new HashSet<>();
        List<EnrollRecord> deduped = new ArrayList<>(parsed.size());
        for (EnrollRecord r : parsed) {
            String key = compositeKey(r);
            if (seenKeys.add(key)) {
                deduped.add(r);
            }
        }
        int duplicateMerged = parsed.size() - deduped.size();
        result.setDuplicateMergedCount(duplicateMerged);
        result.setParsedValidCount(parsed.size());

        // 排序一次
        deduped.sort(EnrollRecord.sortComparator());
        result.setRecords(deduped);
        result.setOutputCount(deduped.size());

        fillTypeStatistics(result, deduped);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        result.setElapsedMs(elapsedMs);
        result.setMessage(String.format(
                "解析合法 %d 条，空行 %d，非法 %d，合并重复 %d，输出 %d 条。",
                parsed.size(), parseOutcome.emptyLineCount(), parseOutcome.invalidLineCount(), duplicateMerged, deduped.size()));
        // 日志使用 ASCII/英文，避免 Windows 默认代码页下控制台中文乱码（业务文案仍以 API 返回中文为准）
        log.info("process elapsed={}ms validLines={} outputLines={} mergedDuplicates={}", elapsedMs, parsed.size(), deduped.size(), duplicateMerged);
        return result;
    }

    /**
     * 从 Excel（.xlsx）首工作表读取选课并走与 CSV 相同的处理链路。
     * <p>
     * 列约定：A 列学生ID、B 列课程ID、C 列课程名称、D 列课程类型（可选）。首行若为表头（含「学生」等关键字）自动跳过。
     */
    public ProcessResult processExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(400, "仅支持 .xlsx 格式，请将表格另存为 xlsx 后再上传");
        }
        long start = System.nanoTime();
        StringBuilder csv = new StringBuilder();
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            if (wb.getNumberOfSheets() <= 0) {
                throw new BusinessException(400, "Excel 中无工作表");
            }
            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int r = 0; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                if (r == 0 && isLikelyHeaderRow(row)) {
                    continue;
                }
                String sid = cellToPlain(row.getCell(0));
                String cid = cellToPlain(row.getCell(1));
                String cname = cellToPlain(row.getCell(2));
                String ctype = cellToPlain(row.getCell(3));
                if (!StringUtils.hasText(sid) && !StringUtils.hasText(cid) && !StringUtils.hasText(cname)) {
                    continue;
                }
                sid = sanitizeCellForCsvLine(sid);
                cid = sanitizeCellForCsvLine(cid);
                cname = sanitizeCellForCsvLine(cname);
                ctype = sanitizeCellForCsvLine(ctype);
                if (StringUtils.hasText(ctype)) {
                    csv.append(sid).append(',').append(cid).append(',').append(cname).append(',').append(ctype).append('\n');
                } else {
                    csv.append(sid).append(',').append(cid).append(',').append(cname).append('\n');
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel parse failed", e);
            throw new BusinessException(500, "Excel 解析失败：" + e.getMessage());
        }
        CsvProcessRequest req = new CsvProcessRequest();
        req.setCsv(csv.toString());
        ProcessResult result = processCsv(req);
        long totalMs = (System.nanoTime() - start) / 1_000_000L;
        result.setMessage("Excel 导入 | " + result.getMessage());
        log.info("Excel import total elapsed={}ms (includes CSV pipeline)", totalMs);
        return result;
    }

    /**
     * 统一检索：多列 AND + 可选「快捷维度关键词」AND；全部为空则返回全量。
     */
    public SearchResult searchUnified(UnifiedSearchRequest request) {
        long start = System.nanoTime();
        SearchResult out = new SearchResult();
        List<EnrollRecord> source = request.getRecords() == null ? List.of() : request.getRecords();

        String sid = trimToNull(request.getStudentId());
        String cid = trimToNull(request.getCourseId());
        String cname = trimToNull(request.getCourseName());
        String ctype = trimToNull(request.getCourseType());
        String quickDim = request.getQuickDimension() == null ? "" : request.getQuickDimension().trim().toUpperCase(Locale.ROOT);
        String quickKw = request.getQuickKeyword() == null ? "" : request.getQuickKeyword().trim();

        boolean hasField = sid != null || cid != null || cname != null || ctype != null;
        boolean hasQuick = StringUtils.hasText(quickKw);
        if (hasQuick && !ALLOWED_SEARCH.contains(quickDim)) {
            throw new BusinessException(400, "快捷检索维度无效，可选：STUDENT_ID、COURSE_ID、COURSE_NAME、COURSE_TYPE");
        }
        if (hasQuick && !StringUtils.hasText(quickDim)) {
            throw new BusinessException(400, "填写了快捷关键词时，请同时选择快捷检索维度");
        }

        if (!hasField && !hasQuick) {
            out.setRecords(new ArrayList<>(source));
            out.setMatched(true);
            out.setMessage(null);
            out.setElapsedMs((System.nanoTime() - start) / 1_000_000L);
            log.info("unifiedSearch elapsed={}ms noCriteria returnAll count={}", out.getElapsedMs(), source.size());
            return out;
        }

        List<EnrollRecord> matched = source.stream()
                .filter(r -> sid == null || containsSafe(r.getStudentId(), sid))
                .filter(r -> cid == null || containsSafe(r.getCourseId(), cid))
                .filter(r -> cname == null || containsSafe(r.getCourseName(), cname))
                .filter(r -> ctype == null || ctype.equals(r.getCourseType()))
                .filter(r -> !hasQuick || matches(r, quickDim, quickKw))
                .collect(Collectors.toCollection(ArrayList::new));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        out.setElapsedMs(elapsedMs);
        if (matched.isEmpty()) {
            out.setMatched(false);
            out.setMessage(NO_MATCH_MESSAGE);
            out.setRecords(List.of());
        } else {
            out.setMatched(true);
            out.setRecords(matched);
        }
        log.info("unifiedSearch elapsed={}ms matched={}", elapsedMs, matched.size());
        return out;
    }

    /**
     * 按字段切换排序：单次排序，不修改入参列表（返回新列表）。
     */
    public List<EnrollRecord> sortRecords(SortRequest request) {
        long start = System.nanoTime();
        List<EnrollRecord> source = request.getRecords() == null ? List.of() : new ArrayList<>(request.getRecords());
        Comparator<EnrollRecord> comparator = comparatorFor(request.getSortField());
        if (!request.isAscending()) {
            comparator = comparator.reversed();
        }
        source.sort(comparator.thenComparing(EnrollRecord::getStudentId, Comparator.nullsLast(String::compareTo))
                .thenComparing(EnrollRecord::getCourseId, Comparator.nullsLast(String::compareTo)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        log.info("sort elapsed={}ms count={} field={} ascending={}", elapsedMs, source.size(), request.getSortField(), request.isAscending());
        return source;
    }

    /**
     * 导出 CSV 文本：校验学号、课号非空。
     */
    public String buildExportCsv(ExportRequest request) {
        validateRecordsHaveIds(request.getRecords());
        StringBuilder sb = new StringBuilder();
        for (EnrollRecord r : request.getRecords()) {
            sb.append(csvEscape(r.getStudentId())).append(',')
                    .append(csvEscape(r.getCourseId())).append(',')
                    .append(csvEscape(r.getCourseName())).append(',')
                    .append(csvEscape(r.getCourseType())).append('\n');
        }
        return sb.toString();
    }

    /**
     * 生成含噪声的测试 CSV：指定目标「合法唯一选课」规模，并混入重复、空行、非法行。
     */
    public String generateMixedTestCsv(int uniqueValidTarget) {
        if (uniqueValidTarget != 100 && uniqueValidTarget != 500 && uniqueValidTarget != 1000) {
            throw new BusinessException(400, "测试数据规模仅支持 100、500、1000");
        }
        long seed = uniqueValidTarget * 31L + 20260514L;
        Random random = new Random(seed);
        List<String> lines = new ArrayList<>();

        LinkedHashSet<String> pairKeys = new LinkedHashSet<>();
        while (pairKeys.size() < uniqueValidTarget) {
            int sidNum = 1 + random.nextInt(900_000);
            int cidNum = 1 + random.nextInt(900_000);
            String sid = formatStudentId(sidNum);
            String cid = formatCourseId(cidNum);
            String key = sid + "|" + cid;
            if (!pairKeys.add(key)) {
                continue;
            }
            String name = COURSE_NAME_POOL[random.nextInt(COURSE_NAME_POOL.length)];
            String[] types = {TYPE_PUBLIC, TYPE_MAJOR, TYPE_ELECTIVE};
            String type = types[random.nextInt(types.length)];
            lines.add(String.join(",", sid, cid, name, type));
        }

        int dupExtra = Math.max(1, uniqueValidTarget / 10);
        for (int i = 0; i < dupExtra; i++) {
            lines.add(lines.get(random.nextInt(lines.size())));
        }

        int emptyExtra = Math.max(3, uniqueValidTarget / 25);
        for (int i = 0; i < emptyExtra; i++) {
            lines.add("");
            lines.add("   ");
        }

        lines.add("S000001,C000001");
        lines.add("BAD,C000001,非法学号,专业课");
        lines.add("S000001,BAD999,非法课号,公共课");
        lines.add("S000001,C000001,,专业课");
        lines.add("仅一列");
        lines.add("S00000X,C000001,格式错误,专业课");

        Collections.shuffle(lines, random);
        return String.join("\n", lines);
    }

    public List<EnrollRecord> sampleRecords() {
        List<EnrollRecord> list = new ArrayList<>();
        list.add(new EnrollRecord("S000001", "C000001", "Java程序设计", TYPE_MAJOR));
        list.add(new EnrollRecord("S000002", "C000003", "计算机网络", TYPE_PUBLIC));
        list.add(new EnrollRecord("S000002", "C000010", "人工智能导论", TYPE_ELECTIVE));
        list.add(new EnrollRecord("S000001", "C000003", "计算机网络", TYPE_PUBLIC));
        return processDedupeSortOnly(list);
    }

    /** 控制台逐行打印（供演示或单元测试调用） */
    public void printRecords(List<EnrollRecord> records) {
        for (EnrollRecord r : records) {
            System.out.println(r.toString());
        }
    }

    // ———————————————————— 私有方法 ————————————————————

    private List<EnrollRecord> processDedupeSortOnly(List<EnrollRecord> input) {
        Set<String> seen = new HashSet<>();
        List<EnrollRecord> out = new ArrayList<>();
        for (EnrollRecord r : input) {
            applyCourseType(r);
            if (seen.add(compositeKey(r))) {
                out.add(r);
            }
        }
        out.sort(EnrollRecord.sortComparator());
        return out;
    }

    private void applyCourseType(EnrollRecord r) {
        if (!StringUtils.hasText(r.getCourseType())) {
            r.setCourseType(inferCourseType(r.getCourseName()));
        } else {
            r.setCourseType(normalizeCourseType(r.getCourseType()));
        }
    }

    private void fillTypeStatistics(ProcessResult result, List<EnrollRecord> list) {
        int pub = 0;
        int maj = 0;
        int ele = 0;
        for (EnrollRecord r : list) {
            String t = r.getCourseType();
            if (TYPE_PUBLIC.equals(t)) {
                pub++;
            } else if (TYPE_MAJOR.equals(t)) {
                maj++;
            } else if (TYPE_ELECTIVE.equals(t)) {
                ele++;
            }
        }
        result.setStatPublic(pub);
        result.setStatMajor(maj);
        result.setStatElective(ele);
    }

    private Comparator<EnrollRecord> comparatorFor(SortField field) {
        return switch (field) {
            case STUDENT_ID -> Comparator.comparing(EnrollRecord::getStudentId, Comparator.nullsLast(String::compareTo));
            case COURSE_ID -> Comparator.comparing(EnrollRecord::getCourseId, Comparator.nullsLast(String::compareTo));
            case COURSE_NAME -> Comparator.comparing(EnrollRecord::getCourseName, Comparator.nullsLast(String::compareTo));
        };
    }

    private void validateRecordsHaveIds(List<EnrollRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new BusinessException(400, "导出数据不能为空");
        }
        int index = 0;
        for (EnrollRecord r : records) {
            index++;
            if (!StringUtils.hasText(r.getStudentId())) {
                throw new BusinessException(400, "学生ID不能为空（第 " + index + " 条）");
            }
            if (!StringUtils.hasText(r.getCourseId())) {
                throw new BusinessException(400, "课程ID不能为空（第 " + index + " 条）");
            }
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String compositeKey(EnrollRecord r) {
        return r.getStudentId() + "\u0001" + r.getCourseId();
    }

    private boolean matches(EnrollRecord r, String searchType, String keyword) {
        return switch (searchType) {
            case SEARCH_STUDENT_ID -> containsSafe(r.getStudentId(), keyword);
            case SEARCH_COURSE_ID -> containsSafe(r.getCourseId(), keyword);
            case SEARCH_COURSE_NAME -> containsSafe(r.getCourseName(), keyword);
            case SEARCH_COURSE_TYPE -> containsSafe(r.getCourseType(), keyword);
            default -> containsSafe(r.getStudentId(), keyword)
                    || containsSafe(r.getCourseId(), keyword)
                    || containsSafe(r.getCourseName(), keyword)
                    || containsSafe(r.getCourseType(), keyword);
        };
    }

    private boolean containsSafe(String field, String keyword) {
        if (!StringUtils.hasText(field) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return field.contains(keyword);
    }

    /**
     * 单次扫描原始文本：统计空行、非法行并收集合法记录。
     */
    private ParseOutcome parseCsvLinesDetailed(String raw) {
        List<EnrollRecord> list = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String[] lines = raw.split("\\R", -1);
        int empty = 0;
        int invalid = 0;
        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            if (line == null || line.isBlank()) {
                empty++;
                continue;
            }
            String trimmed = line.trim();
            String[] parts = trimmed.split(",", 4);
            if (parts.length < 3) {
                invalid++;
                addWarning(warnings, "第" + lineNo + "行：字段缺失（至少需要 学生ID,课程ID,课程名称）");
                continue;
            }
            String sid = parts[0].trim();
            String cid = parts[1].trim();
            String cname = parts[2].trim();
            String ctype = parts.length >= 4 ? parts[3].trim() : null;

            if (!StringUtils.hasText(sid)) {
                invalid++;
                addWarning(warnings, "第" + lineNo + "行：学生ID不能为空");
                continue;
            }
            if (!StringUtils.hasText(cid)) {
                invalid++;
                addWarning(warnings, "第" + lineNo + "行：课程ID不能为空");
                continue;
            }
            if (!STUDENT_ID_PATTERN.matcher(sid).matches()) {
                invalid++;
                addWarning(warnings, "第" + lineNo + "行：学生ID格式非法（需 S+6 位数字）");
                continue;
            }
            if (!COURSE_ID_PATTERN.matcher(cid).matches()) {
                invalid++;
                addWarning(warnings, "第" + lineNo + "行：课程ID格式非法（需 C+6 位数字）");
                continue;
            }
            if (!StringUtils.hasText(cname)) {
                invalid++;
                addWarning(warnings, "第" + lineNo + "行：课程名称不能为空");
                continue;
            }
            list.add(new EnrollRecord(sid, cid, cname, StringUtils.hasText(ctype) ? ctype : null));
        }
        return new ParseOutcome(list, lines.length, empty, invalid, warnings);
    }

    private void addWarning(List<String> warnings, String text) {
        if (warnings.size() >= MAX_WARNINGS) {
            return;
        }
        warnings.add(text);
    }

    private String normalizeCourseType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return TYPE_ELECTIVE;
        }
        String t = raw.trim();
        if (TYPE_PUBLIC.equals(t) || TYPE_MAJOR.equals(t) || TYPE_ELECTIVE.equals(t)) {
            return t;
        }
        return inferCourseType(t);
    }

    /**
     * 无第四列时的简单自动识别：结合课程名关键词与常见命名。
     */
    private String inferCourseType(String courseName) {
        if (!StringUtils.hasText(courseName)) {
            return TYPE_ELECTIVE;
        }
        String n = courseName;
        if (n.contains("体育") || n.contains("外语") || n.contains("英语") || n.contains("概论")
                || n.contains("思想") || n.contains("军事") || n.contains("心理健康")) {
            return TYPE_PUBLIC;
        }
        if (n.contains("选修") || n.contains("通识") || (n.contains("导论") && n.length() <= 8)) {
            return TYPE_ELECTIVE;
        }
        if (n.contains("程序") || n.contains("网络") || n.contains("数据库") || n.contains("算法")
                || n.contains("结构") || n.contains("系统") || n.contains("Java")) {
            return TYPE_MAJOR;
        }
        return TYPE_ELECTIVE;
    }

    private String formatStudentId(int n) {
        return String.format(Locale.ROOT, "S%06d", n);
    }

    private String formatCourseId(int n) {
        return String.format(Locale.ROOT, "C%06d", n);
    }

    private String csvEscape(String v) {
        if (v == null) {
            return "";
        }
        boolean needQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        String s = v.replace("\"", "\"\"");
        return needQuote ? "\"" + s + "\"" : s;
    }

    /**
     * 判断首行是否为表头（避免把数据行当表头误删）。
     */
    private boolean isLikelyHeaderRow(Row row) {
        String a = cellToPlain(row.getCell(0));
        String b = cellToPlain(row.getCell(1));
        String c = cellToPlain(row.getCell(2));
        if (a.contains("学生") && (a.contains("ID") || a.contains("学号"))) {
            return true;
        }
        if (b.contains("课程") && b.toUpperCase(Locale.ROOT).contains("ID")) {
            return true;
        }
        if (c.contains("课程") && c.contains("名称")) {
            return true;
        }
        return "student_id".equalsIgnoreCase(a) || "course_id".equalsIgnoreCase(b);
    }

    /**
     * 单元格转字符串（兼容数字、公式、日期）。
     */
    private static String cellToPlain(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                if (v == Math.rint(v) && v >= -1e12 && v <= 1e12) {
                    yield String.format(Locale.ROOT, "%.0f", v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        double v = cell.getNumericCellValue();
                        if (v == Math.rint(v)) {
                            yield String.format(Locale.ROOT, "%.0f", v);
                        }
                        yield String.valueOf(v);
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            case BLANK -> "";
            default -> "";
        };
    }

    /**
     * 写入合成 CSV 前：去掉换行并将英文逗号替换为全角逗号，避免拆列错乱。
     */
    private static String sanitizeCellForCsvLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\r', ' ').replace('\n', ' ').replace(',', '，');
    }

    /**
     * CSV 解析中间结果（单文件内一次扫描产出）。
     */
    private record ParseOutcome(List<EnrollRecord> records, int rawLineCount, int emptyLineCount, int invalidLineCount,
                                List<String> warnings) {
    }
}
