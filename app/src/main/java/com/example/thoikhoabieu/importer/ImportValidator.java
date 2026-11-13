package com.example.thoikhoabieu.importer;

import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.DateUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm tra hợp lệ từng dòng dữ liệu và phát hiện xung đột / trùng lặp:
 * 1. Kiểm tra định dạng trường: Tên môn, Ngày (dd/MM/yyyy hoặc yyyy-MM-dd), Giờ (HH:mm), kết thúc > bắt đầu.
 * 2. Kiểm tra trùng hoàn toàn với cơ sở dữ liệu SQLite (Exact Duplicate).
 * 3. Kiểm tra trùng giờ / vi phạm khoảng nghỉ GAP 1 phút với SQLite (DB Conflict).
 * 4. Kiểm tra trùng giờ / vi phạm khoảng nghỉ GAP 1 phút với các dòng trước trong cùng tệp (In-file Conflict).
 */
public final class ImportValidator {

    private static final Pattern DATE_DMY = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
    private static final Pattern DATE_YMD = Pattern.compile("^(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})$");
    private static final Pattern TIME_HM = Pattern.compile("^(\\d{1,2})[:hH](\\d{2})$");

    private ImportValidator() {
    }

    /**
     * Bước 1: Kiểm tra tính hợp lệ về mặt định dạng của từng dòng.
     */
    public static ImportPreviewItem validateFormat(ScheduleCsvParser.CsvRow row, Map<String, Integer> headerMap) {
        String subject = getCol(row, headerMap, "subject");
        String dateStr = getCol(row, headerMap, "date");
        String startStr = getCol(row, headerMap, "start");
        String endStr = getCol(row, headerMap, "end");
        String room = getCol(row, headerMap, "room");
        String className = getCol(row, headerMap, "class");
        String statusStr = getCol(row, headerMap, "status");
        String note = getCol(row, headerMap, "note");

        String rawSummary = String.format(Locale.US, "Dòng %d: %s | %s | %s-%s", 
                row.lineNumber, subject.isEmpty() ? "(Thiếu môn)" : subject, dateStr, startStr, endStr);

        // 1. Kiểm tra tên môn học
        if (subject.trim().isEmpty()) {
            return new ImportPreviewItem(row.lineNumber, null, ImportPreviewItem.StatusType.INVALID_FORMAT,
                    "Tên môn học không được để trống.", rawSummary);
        }

        // 2. Kiểm tra ngày học
        String isoDate = parseIsoDate(dateStr);
        if (isoDate == null) {
            return new ImportPreviewItem(row.lineNumber, null, ImportPreviewItem.StatusType.INVALID_FORMAT,
                    "Ngày học không đúng định dạng (cần dd/MM/yyyy hoặc yyyy-MM-dd). Nhận được: \"" + dateStr + "\"", rawSummary);
        }

        // 3. Kiểm tra giờ bắt đầu
        int startMin = parseMinutes(startStr);
        if (startMin < 0) {
            return new ImportPreviewItem(row.lineNumber, null, ImportPreviewItem.StatusType.INVALID_FORMAT,
                    "Giờ bắt đầu không hợp lệ (cần HH:mm). Nhận được: \"" + startStr + "\"", rawSummary);
        }

        // 4. Kiểm tra giờ kết thúc
        int endMin = parseMinutes(endStr);
        if (endMin < 0) {
            return new ImportPreviewItem(row.lineNumber, null, ImportPreviewItem.StatusType.INVALID_FORMAT,
                    "Giờ kết thúc không hợp lệ (cần HH:mm). Nhận được: \"" + endStr + "\"", rawSummary);
        }

        // 5. Kiểm tra kết thúc phải sau bắt đầu
        if (endMin <= startMin) {
            return new ImportPreviewItem(row.lineNumber, null, ImportPreviewItem.StatusType.INVALID_FORMAT,
                    "Giờ kết thúc (" + DateUtil.hm(endMin) + ") phải sau giờ bắt đầu (" + DateUtil.hm(startMin) + ").", rawSummary);
        }

        Schedule s = new Schedule();
        s.subject = subject.trim();
        s.date = isoDate;
        s.startMin = startMin;
        s.endMin = endMin;
        s.room = room.trim();
        s.className = className.trim();
        s.note = note.trim();
        s.status = parseStatus(statusStr);

        return new ImportPreviewItem(row.lineNumber, s, ImportPreviewItem.StatusType.VALID, "Dữ liệu hợp lệ", rawSummary);
    }

    /**
     * Bước 2: Kiểm tra xung đột và trùng lặp với SQLite và giữa các dòng trong cùng file.
     */
    public static void checkConflictsAndDuplicates(List<ImportPreviewItem> items, ScheduleRepository repo) {
        List<Schedule> acceptedInBatch = new ArrayList<>();
        List<Integer> acceptedLineNumbers = new ArrayList<>();

        for (ImportPreviewItem item : items) {
            if (item.statusType != ImportPreviewItem.StatusType.VALID || item.schedule == null) {
                continue; // dòng bị lỗi format từ trước
            }

            Schedule s = item.schedule;

            // 1. Kiểm tra trùng lặp hoàn toàn với DB
            if (repo != null && repo.isExactDuplicate(s)) {
                item.statusType = ImportPreviewItem.StatusType.DUPLICATE_DB;
                item.message = "Đã có buổi học này trên máy (trùng môn, phòng, ngày và giờ).";
                item.willImport = false;
                continue;
            }

            // 2. Kiểm tra xung đột giờ học với DB (vẫn cho phép nhập nhưng đánh dấu cảnh báo trùng)
            boolean conflictWithDb = false;
            if (repo != null) {
                List<Schedule> dbConflicts = repo.findConflicts(s.date, 
                        Math.max(0, s.startMin - ScheduleRepository.GAP), 
                        s.endMin + ScheduleRepository.GAP, 
                        null);
                if (!dbConflicts.isEmpty()) {
                    Schedule firstConf = dbConflicts.get(0);
                    item.statusType = ImportPreviewItem.StatusType.CONFLICT_DB;
                    item.message = "Trùng giờ với môn: " 
                            + firstConf.subject + " (" + firstConf.timeRange() + ") đã có trên máy (vẫn được lưu vào hệ thống).";
                    item.willImport = true;
                    acceptedInBatch.add(s);
                    acceptedLineNumbers.add(item.lineNumber);
                    conflictWithDb = true;
                }
            }

            if (conflictWithDb) {
                continue;
            }

            // 3. Kiểm tra xung đột với các dòng phía trước trong cùng tệp CSV (vẫn cho phép nhập)
            boolean inBatchConflict = false;
            for (int i = 0; i < acceptedInBatch.size(); i++) {
                Schedule prior = acceptedInBatch.get(i);
                if (prior.date.equals(s.date)) {
                    // Kiểm tra giao khoảng: s1 < e2 && s2 < e1 (có tính GAP)
                    int s1 = Math.max(0, prior.startMin - ScheduleRepository.GAP);
                    int e1 = prior.endMin + ScheduleRepository.GAP;
                    int s2 = s.startMin;
                    int e2 = s.endMin;

                    if (s1 < e2 && s2 < e1) {
                        item.statusType = ImportPreviewItem.StatusType.CONFLICT_FILE;
                        item.message = "Trùng khung giờ học với Dòng " + acceptedLineNumbers.get(i) 
                                + " (" + prior.subject + ", " + prior.timeRange() + ") trong tệp tin (vẫn được lưu vào hệ thống).";
                        item.willImport = true;
                        inBatchConflict = true;
                        acceptedInBatch.add(s);
                        acceptedLineNumbers.add(item.lineNumber);
                        break;
                    }
                }
            }

            if (!inBatchConflict) {
                // Đủ điều kiện hợp lệ
                item.statusType = ImportPreviewItem.StatusType.VALID;
                item.message = "Sẵn sàng nhập vào cơ sở dữ liệu.";
                item.willImport = true;
                acceptedInBatch.add(s);
                acceptedLineNumbers.add(item.lineNumber);
            }
        }
    }

    private static String getCol(ScheduleCsvParser.CsvRow row, Map<String, Integer> headerMap, String key) {
        Integer idx = headerMap.get(key);
        return (idx != null) ? row.get(idx) : "";
    }

    public static String parseIsoDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();

        // 1. Dạng dd/MM/yyyy hoặc dd-MM-yyyy
        Matcher m1 = DATE_DMY.matcher(s);
        if (m1.matches()) {
            int d = Integer.parseInt(m1.group(1));
            int m = Integer.parseInt(m1.group(2));
            int y = Integer.parseInt(m1.group(3));
            if (isValidDate(y, m, d)) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
            }
            return null;
        }

        // 2. Dạng yyyy-MM-dd hoặc yyyy/MM/dd
        Matcher m2 = DATE_YMD.matcher(s);
        if (m2.matches()) {
            int y = Integer.parseInt(m2.group(1));
            int m = Integer.parseInt(m2.group(2));
            int d = Integer.parseInt(m2.group(3));
            if (isValidDate(y, m, d)) {
                return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
            }
            return null;
        }

        return null;
    }

    private static boolean isValidDate(int year, int month, int day) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) {
            return false;
        }
        Calendar cal = new GregorianCalendar(year, month - 1, 1);
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        return day <= maxDay;
    }

    public static int parseMinutes(String raw) {
        if (raw == null) return -1;
        String s = raw.trim();
        Matcher m = TIME_HM.matcher(s);
        if (m.matches()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h >= 0 && h <= 23 && min >= 0 && min <= 59) {
                return h * 60 + min;
            }
        }
        return -1;
    }

    public static String parseStatus(String raw) {
        if (raw == null) return Schedule.DANG_HOC;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("hoàn thành") || s.contains("hoan thanh") || s.equals("done") || s.equals("hoan_thanh")) {
            return Schedule.HOAN_THANH;
        }
        if (s.contains("tạm nghỉ") || s.contains("tam nghi") || s.equals("pause") || s.equals("tam_nghi")) {
            return Schedule.TAM_NGHI;
        }
        return Schedule.DANG_HOC;
    }
}
