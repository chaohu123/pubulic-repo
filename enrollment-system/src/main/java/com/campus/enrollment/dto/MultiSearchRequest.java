package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 多条件组合检索：各条件之间为 AND；字段留空或 null 表示不参与过滤。
 */
public class MultiSearchRequest {

    @NotNull(message = "选课记录列表不能为空")
    @Valid
    private List<EnrollRecord> records = new ArrayList<>();

    /** 学生 ID 包含匹配（可填片段，如 S0001） */
    private String studentId;

    /** 课程 ID 包含匹配 */
    private String courseId;

    /** 课程名称包含匹配 */
    private String courseName;

    /** 课程类型精确匹配（公共课/专业课/选修课） */
    private String courseType;

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
}
