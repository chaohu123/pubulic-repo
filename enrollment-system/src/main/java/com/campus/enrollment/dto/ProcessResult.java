package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 处理结果：含统计、告警与性能信息。
 */
public class ProcessResult {

    private boolean success = true;
    private String message;
    private List<EnrollRecord> records = new ArrayList<>();
    /** 服务端处理总耗时（毫秒） */
    private long elapsedMs;
    /** 成功解析为合法选课行的数量（去重前） */
    private int parsedValidCount;
    /** 去重排序后的输出行数 */
    private int outputCount;
    /** 原始文本按行切分后的总行数（含空行） */
    private int rawLineCount;
    /** 空行数量 */
    private int emptyLineCount;
    /** 非法行数量（字段缺失、格式错误等） */
    private int invalidLineCount;
    /** 同一文件内重复（学号+课号）被合并掉的条数 */
    private int duplicateMergedCount;
    /** 解析告警（截断展示，避免响应过大） */
    private List<String> warnings = new ArrayList<>();
    /** 公共课条数（输出结果统计） */
    private int statPublic;
    /** 专业课条数 */
    private int statMajor;
    /** 选修课条数 */
    private int statElective;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<EnrollRecord> getRecords() {
        return records;
    }

    public void setRecords(List<EnrollRecord> records) {
        this.records = records;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public int getParsedValidCount() {
        return parsedValidCount;
    }

    public void setParsedValidCount(int parsedValidCount) {
        this.parsedValidCount = parsedValidCount;
    }

    public int getOutputCount() {
        return outputCount;
    }

    public void setOutputCount(int outputCount) {
        this.outputCount = outputCount;
    }

    public int getRawLineCount() {
        return rawLineCount;
    }

    public void setRawLineCount(int rawLineCount) {
        this.rawLineCount = rawLineCount;
    }

    public int getEmptyLineCount() {
        return emptyLineCount;
    }

    public void setEmptyLineCount(int emptyLineCount) {
        this.emptyLineCount = emptyLineCount;
    }

    public int getInvalidLineCount() {
        return invalidLineCount;
    }

    public void setInvalidLineCount(int invalidLineCount) {
        this.invalidLineCount = invalidLineCount;
    }

    public int getDuplicateMergedCount() {
        return duplicateMergedCount;
    }

    public void setDuplicateMergedCount(int duplicateMergedCount) {
        this.duplicateMergedCount = duplicateMergedCount;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public int getStatPublic() {
        return statPublic;
    }

    public void setStatPublic(int statPublic) {
        this.statPublic = statPublic;
    }

    public int getStatMajor() {
        return statMajor;
    }

    public void setStatMajor(int statMajor) {
        this.statMajor = statMajor;
    }

    public int getStatElective() {
        return statElective;
    }

    public void setStatElective(int statElective) {
        this.statElective = statElective;
    }

    /** 兼容旧字段命名：与 parsedValidCount 相同 */
    @Deprecated
    public int getImportedCount() {
        return parsedValidCount;
    }

    @Deprecated
    public void setImportedCount(int importedCount) {
        this.parsedValidCount = importedCount;
    }
}
