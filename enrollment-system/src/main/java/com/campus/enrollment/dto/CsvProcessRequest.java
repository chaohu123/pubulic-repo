package com.campus.enrollment.dto;

import jakarta.validation.constraints.NotBlank;

public class CsvProcessRequest {

    @NotBlank(message = "CSV 内容不能为空")
    private String csv;

    public String getCsv() {
        return csv;
    }

    public void setCsv(String csv) {
        this.csv = csv;
    }
}
