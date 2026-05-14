package com.campus.enrollment.dto;

import com.campus.enrollment.entity.EnrollRecord;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {

    private boolean matched = true;
    private String message;
    private List<EnrollRecord> records = new ArrayList<>();
    private long elapsedMs;

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
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
}
