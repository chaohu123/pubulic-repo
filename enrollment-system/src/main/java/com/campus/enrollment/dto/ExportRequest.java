package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 导出 CSV 请求体。
 */
public class ExportRequest {

    @NotEmpty(message = "导出数据不能为空")
    @Valid
    private List<EnrollRecord> records;

    public List<EnrollRecord> getRecords() {
        return records;
    }

    public void setRecords(List<EnrollRecord> records) {
        this.records = records;
    }
}
