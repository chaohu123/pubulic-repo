package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一检索请求：合并「单维度关键词」与「多列组合条件」。
 * <p>
 * 规则：所有已填写的条件之间为 <b>AND</b> 关系。
 * <ul>
 *   <li>学生ID / 课程ID / 课程名称：非空时做「包含」匹配（子串）</li>
 *   <li>课程类型：非空时做「精确」匹配</li>
 *   <li>快捷维度 + 快捷关键词：两者均非空时，在指定维度上做「包含」匹配，并与上述条件 AND</li>
 * </ul>
 * 若所有条件均为空，则返回当前记录全量（不报错）。
 */
public class UnifiedSearchRequest {

    @NotNull(message = "选课记录列表不能为空")
    @Valid
    private List<EnrollRecord> records = new ArrayList<>();

    /** 学生 ID 子串（可选） */
    private String studentId;
    /** 课程 ID 子串（可选） */
    private String courseId;
    /** 课程名称子串（可选） */
    private String courseName;
    /** 课程类型精确值：公共课 / 专业课 / 选修课（可选） */
    private String courseType;

    /**
     * 快捷检索维度：STUDENT_ID、COURSE_ID、COURSE_NAME、COURSE_TYPE（可选，需与 quickKeyword 同时使用）。
     */
    private String quickDimension;
    /** 快捷检索关键词（可选，需与 quickDimension 同时使用） */
    private String quickKeyword;

    public List<EnrollRecord> getRecords() {
        return records;
    }

    public void setRecords(List<EnrollRecord> records) {
        this.records = records;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getCourseType() {
        return courseType;
    }

    public void setCourseType(String courseType) {
        this.courseType = courseType;
    }

    public String getQuickDimension() {
        return quickDimension;
    }

    public void setQuickDimension(String quickDimension) {
        this.quickDimension = quickDimension;
    }

    public String getQuickKeyword() {
        return quickKeyword;
    }

    public void setQuickKeyword(String quickKeyword) {
        this.quickKeyword = quickKeyword;
    }
}
