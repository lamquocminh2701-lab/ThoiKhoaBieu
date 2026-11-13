package com.example.thoikhoabieu.importer;

import com.example.thoikhoabieu.model.Schedule;

/**
 * Đại diện cho một dòng dữ liệu xem trước khi nhập từ tệp CSV.
 */
public class ImportPreviewItem {

    public enum StatusType {
        /** Hợp lệ, sẵn sàng nhập vào database. */
        VALID,
        /** Trùng hoàn toàn với một buổi học đã tồn tại trong database (bỏ qua). */
        DUPLICATE_DB,
        /** Trùng giờ hoặc vi phạm khoảng nghỉ GAP 1 phút với lịch đã có trong database. */
        CONFLICT_DB,
        /** Trùng giờ hoặc vi phạm khoảng nghỉ GAP 1 phút với một dòng phía trên trong cùng tệp CSV. */
        CONFLICT_FILE,
        /** Lỗi định dạng dữ liệu (ngày, giờ, thiếu tên môn học...). */
        INVALID_FORMAT
    }

    public final int lineNumber;
    public final Schedule schedule;
    public StatusType statusType;
    public String message;
    public boolean willImport;
    public final String rawPreview;

    public ImportPreviewItem(int lineNumber, Schedule schedule, StatusType statusType, String message, String rawPreview) {
        this.lineNumber = lineNumber;
        this.schedule = schedule;
        this.statusType = statusType;
        this.message = message;
        this.willImport = (statusType == StatusType.VALID);
        this.rawPreview = rawPreview;
    }

    public boolean isConflict() {
        return statusType == StatusType.CONFLICT_DB || statusType == StatusType.CONFLICT_FILE;
    }

    public boolean isDuplicate() {
        return statusType == StatusType.DUPLICATE_DB;
    }

    public boolean isError() {
        return statusType == StatusType.INVALID_FORMAT;
    }

    public boolean isValid() {
        return statusType == StatusType.VALID;
    }
}
