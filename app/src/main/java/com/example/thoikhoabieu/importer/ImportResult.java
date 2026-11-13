package com.example.thoikhoabieu.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả tổng kết của quá trình kiểm tra và nhập lịch học hàng loạt.
 */
public class ImportResult {
    public int totalLines = 0;
    public int validCount = 0;
    public int importedCount = 0;
    public int duplicateDbCount = 0;
    public int conflictDbCount = 0;
    public int conflictFileCount = 0;
    public int formatErrorCount = 0;
    public final List<String> details = new ArrayList<>();
    public long durationMs = 0;

    public int totalConflicts() {
        return conflictDbCount + conflictFileCount;
    }

    public int totalSkipped() {
        return duplicateDbCount + totalConflicts() + formatErrorCount;
    }
}
