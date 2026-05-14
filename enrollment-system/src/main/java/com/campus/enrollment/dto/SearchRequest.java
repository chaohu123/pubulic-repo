package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SearchRequest {

    @NotNull
    private List<EnrollRecord> records = new ArrayList<>();

    /** STUDENT_ID | COURSE_ID | COURSE_NAME | COURSE_TYPE */
    @NotBlank
    private String searchType;

    private String keyword;

    public List<EnrollRecord> getRecords() {
        return records;
    }

    public void setRecords(List<EnrollRecord> records) {
        this.records = records;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
