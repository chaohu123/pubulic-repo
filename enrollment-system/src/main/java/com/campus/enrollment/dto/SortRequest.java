package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 排序请求：对已有选课列表按指定字段重排。
 */
public class SortRequest {

    @NotNull(message = "选课记录列表不能为空")
    @Valid
    private List<EnrollRecord> records = new ArrayList<>();

    @NotNull(message = "排序字段不能为空")
    private SortField sortField;

    /** true 升序，false 降序 */
    private boolean ascending = true;

    public List<EnrollRecord> getRecords() {
        return records;
    }

    public void setRecords(List<EnrollRecord> records) {
        this.records = records;
    }

    public SortField getSortField() {
        return sortField;
    }

    public void setSortField(SortField sortField) {
        this.sortField = sortField;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }
}
