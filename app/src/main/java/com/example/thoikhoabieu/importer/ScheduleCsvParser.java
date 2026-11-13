package com.example.thoikhoabieu.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bộ phân tích cú pháp tệp CSV theo chuẩn RFC 4180:
 * - Hỗ trợ UTF-8 và UTF-8 BOM (\uFEFF).
 * - Hỗ trợ các trường trích dẫn chứa dấu phẩy, dấu nháy kép escaping ("") và xuống dòng.
 * - Tự động nhận diện dòng tiêu đề học kỳ (như file xuất từ ExportUtil) hoặc dòng cột chuẩn.
 * - Giới hạn kích thước tệp tối đa 5MB và số dòng tối đa 5.000 dòng để bảo vệ bộ nhớ.
 */
public final class ScheduleCsvParser {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    public static final int MAX_ROW_COUNT = 5000;

    public static class CsvRow {
        public final int lineNumber;
        public final List<String> columns;

        public CsvRow(int lineNumber, List<String> columns) {
            this.lineNumber = lineNumber;
            this.columns = columns;
        }

        public String get(int index) {
            return (index >= 0 && index < columns.size()) ? columns.get(index) : "";
        }
    }

    public static class ParseResult {
        public boolean success;
        public String errorMessage;
        public String semesterLabel;
        public List<String> headers = new ArrayList<>();
        public Map<String, Integer> headerIndexMap = new HashMap<>();
        public List<CsvRow> dataRows = new ArrayList<>();
    }

    private ScheduleCsvParser() {
    }

    public static ParseResult parse(InputStream inputStream, long streamLength) {
        ParseResult result = new ParseResult();

        if (streamLength > MAX_FILE_SIZE) {
            result.success = false;
            result.errorMessage = "Kích thước tệp vượt quá giới hạn cho phép (tối đa 5 MB).";
            return result;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            long totalRead = 0;
            while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
                totalRead += read;
                if (totalRead > MAX_FILE_SIZE) {
                    result.success = false;
                    result.errorMessage = "Kích thước tệp vượt quá giới hạn 5 MB.";
                    return result;
                }
                sb.append(buffer, 0, read);
            }

            String content = sb.toString();
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1); // Loại bỏ UTF-8 BOM
            }

            if (content.trim().isEmpty()) {
                result.success = false;
                result.errorMessage = "Tệp tin rỗng, không chứa dữ liệu.";
                return result;
            }

            List<List<String>> rawRows = parseCsvTokens(content);
            if (rawRows.isEmpty()) {
                result.success = false;
                result.errorMessage = "Không tìm thấy dòng dữ liệu nào trong tệp CSV.";
                return result;
            }

            if (rawRows.size() > MAX_ROW_COUNT) {
                result.success = false;
                result.errorMessage = "Số lượng dòng trong tệp (" + rawRows.size() + ") vượt quá giới hạn cho phép (5.000 dòng).";
                return result;
            }

            int headerLineIndex = 0;
            List<String> firstRow = rawRows.get(0);

            // Kiểm tra xem dòng 1 có phải là nhãn học kỳ (kiểu ExportUtil: "Học kỳ I · 2026–2027")
            if (firstRow.size() <= 2 && (firstRow.get(0).toLowerCase(Locale.ROOT).contains("học kỳ") 
                    || firstRow.get(0).toLowerCase(Locale.ROOT).contains("hoc ky")
                    || !isHeaderRow(firstRow))) {
                result.semesterLabel = firstRow.get(0);
                headerLineIndex = 1;
            }

            if (headerLineIndex >= rawRows.size()) {
                result.success = false;
                result.errorMessage = "Tệp chỉ có dòng tiêu đề học kỳ, không có các cột thời khóa biểu.";
                return result;
            }

            List<String> headerTokens = rawRows.get(headerLineIndex);
            if (!isHeaderRow(headerTokens)) {
                result.success = false;
                result.errorMessage = "Không tìm thấy dòng tiêu đề hợp lệ (cần chứa các cột: Ngày, Giờ bắt đầu, Giờ kết thúc, Môn học).";
                return result;
            }

            result.headers = headerTokens;
            for (int i = 0; i < headerTokens.size(); i++) {
                String normalized = normalizeHeader(headerTokens.get(i));
                result.headerIndexMap.put(normalized, i);
            }

            // Đọc các dòng dữ liệu
            for (int r = headerLineIndex + 1; r < rawRows.size(); r++) {
                List<String> cols = rawRows.get(r);
                boolean allEmpty = true;
                for (String c : cols) {
                    if (!c.trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (!allEmpty) {
                    result.dataRows.add(new CsvRow(r + 1, cols));
                }
            }

            if (result.dataRows.isEmpty()) {
                result.success = false;
                result.errorMessage = "Tệp tin chỉ có dòng tiêu đề cột, chưa có bản ghi lịch học nào.";
                return result;
            }

            result.success = true;
            return result;

        } catch (IOException e) {
            result.success = false;
            result.errorMessage = "Lỗi khi đọc tệp tin: " + e.getMessage();
            return result;
        }
    }

    private static boolean isHeaderRow(List<String> row) {
        int matchCount = 0;
        for (String cell : row) {
            String norm = normalizeHeader(cell);
            if (norm.equals("date") || norm.equals("subject") || norm.equals("start") || norm.equals("end") 
                    || norm.equals("room") || norm.equals("class")) {
                matchCount++;
            }
        }
        return matchCount >= 2;
    }

    public static String normalizeHeader(String header) {
        if (header == null) return "";
        String h = header.trim().toLowerCase(Locale.ROOT);
        if (h.contains("ngày") || h.contains("ngay") || h.equals("date")) return "date";
        if (h.contains("bắt đầu") || h.contains("bat dau") || h.equals("start")) return "start";
        if (h.contains("kết thúc") || h.contains("ket thuc") || h.equals("end")) return "end";
        if (h.contains("môn") || h.contains("mon") || h.equals("subject")) return "subject";
        if (h.contains("lớp") || h.contains("lop") || h.equals("class") || h.equals("classname")) return "class";
        if (h.contains("phòng") || h.contains("phong") || h.equals("room")) return "room";
        if (h.contains("trạng thái") || h.contains("trang thai") || h.equals("status")) return "status";
        if (h.contains("ghi chú") || h.contains("ghi chu") || h.equals("note")) return "note";
        if (h.contains("tiết") || h.contains("tiet") || h.equals("periods")) return "periods";
        if (h.contains("thứ") || h.contains("thu") || h.equals("dow") || h.equals("day")) return "dow";
        return h;
    }

    private static List<List<String>> parseCsvTokens(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;
        int len = content.length();

        for (int i = 0; i < len; i++) {
            char ch = content.charAt(i);

            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        currentCell.append('"');
                        i++; // bỏ qua ký tự quote thứ 2 (escaped)
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentCell.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    currentRow.add(currentCell.toString().trim());
                    currentCell.setLength(0);
                } else if (ch == '\r') {
                    if (i + 1 < len && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentRow.add(currentCell.toString().trim());
                    currentCell.setLength(0);
                    if (hasNonEmptyCell(currentRow)) {
                        rows.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                } else if (ch == '\n') {
                    currentRow.add(currentCell.toString().trim());
                    currentCell.setLength(0);
                    if (hasNonEmptyCell(currentRow)) {
                        rows.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                } else {
                    currentCell.append(ch);
                }
            }
        }

        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString().trim());
            if (hasNonEmptyCell(currentRow)) {
                rows.add(currentRow);
            }
        }

        return rows;
    }

    private static boolean hasNonEmptyCell(List<String> row) {
        for (String c : row) {
            if (!c.trim().isEmpty()) return true;
        }
        return false;
    }
}
