package com.campus.enrollment.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Comparator;
import java.util.Objects;

/**
 * 选课记录实体：去重语义仅依赖 studentId + courseId。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrollRecord {

    @NotBlank(message = "学生ID不能为空")
    private String studentId;
    @NotBlank(message = "课程ID不能为空")
    private String courseId;
    private String courseName;
    /** 公共课 / 专业课 / 选修课 */
    private String courseType;

    public EnrollRecord() {
    }

    public EnrollRecord(String studentId, String courseId, String courseName, String courseType) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.courseName = courseName;
        this.courseType = courseType;
    }

    public static Comparator<EnrollRecord> sortComparator() {
        return Comparator.comparing(EnrollRecord::getStudentId, Comparator.nullsLast(String::compareTo))
                .thenComparing(EnrollRecord::getCourseId, Comparator.nullsLast(String::compareTo));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EnrollRecord that)) {
            return false;
        }
        return Objects.equals(studentId, that.studentId) && Objects.equals(courseId, that.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, courseId);
    }

    @Override
    public String toString() {
        return String.format("学生ID：%s，课程ID：%s，课程名称：%s", studentId, courseId, courseName);
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
