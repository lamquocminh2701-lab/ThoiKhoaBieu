package com.example.thoikhoabieu.trash;

import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.DateUtil;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Trình xuất nhật ký dọn dẹp lịch học sang định dạng văn bản TXT chuẩn UTF-8:
 * - Ghi lại đầy đủ thông tin các buổi học đã được đưa vào thùng rác.
 * - Hiển thị số lượng, thời gian đưa vào thùng rác và thời điểm xóa vĩnh viễn sau 6 ngày.
 */
public final class TrashLogExporter {

    private TrashLogExporter() {
    }

    public static String buildLogContent(List<Schedule> schedules) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        long now = System.currentTimeMillis();
        long purgeEstimate = now + 6 * 86_400_000L;

        sb.append("================================================================================\n");
        sb.append("NHẬT KÝ DỌN DẸP LỊCH HỌC — THỜI KHÓA BIỂU CÁ NHÂN\n");
        sb.append("================================================================================\n");
        sb.append("Thời gian tạo: ").append(sdf.format(new Date(now))).append("\n");
        sb.append("Số lượng buổi học dọn dẹp: ").append(schedules != null ? schedules.size() : 0).append("\n");
        sb.append("Thời gian lưu trữ trong thùng rác: 6 ngày\n");
        sb.append("Ngày xóa vĩnh viễn dự kiến: ").append(sdf.format(new Date(purgeEstimate))).append("\n");
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("DANH SÁCH CHI TIẾT\n");
        sb.append("--------------------------------------------------------------------------------\n");

        if (schedules != null && !schedules.isEmpty()) {
            for (int i = 0; i < schedules.size(); i++) {
                Schedule s = schedules.get(i);
                Calendar cal = DateUtil.parse(s.date);
                String dow = DateUtil.dowFull(cal);

                sb.append(String.format(Locale.US, "[%d] Môn học: %s\n", i + 1, s.subject));
                sb.append(String.format(Locale.US, "    Lớp: %s | Phòng: %s\n", 
                        (s.className != null && !s.className.isEmpty()) ? s.className : "Chưa có", 
                        (s.room != null && !s.room.isEmpty()) ? s.room : "Chưa có"));
                sb.append(String.format(Locale.US, "    Ngày học: %s (%s) | Thời gian: %s (%d tiết)\n",
                        s.date, dow, s.timeRange(), s.periods()));
                sb.append(String.format(Locale.US, "    Trạng thái: %s\n", Schedule.statusLabel(s.status)));
                sb.append(String.format(Locale.US, "    Ghi chú: %s\n", (s.hasNote() ? s.note.trim() : "Không có")));

                long trashedTime = (s.trashedAt != null && s.trashedAt > 0) ? s.trashedAt : now;
                long purgeTime = (s.purgeAfter != null && s.purgeAfter > 0) ? s.purgeAfter : (trashedTime + 6 * 86_400_000L);

                sb.append(String.format(Locale.US, "    Đưa vào thùng rác lúc: %s\n", sdf.format(new Date(trashedTime))));
                sb.append(String.format(Locale.US, "    Hạn khôi phục: %s\n", sdf.format(new Date(purgeTime))));
                sb.append("--------------------------------------------------------------------------------\n");
            }
        } else {
            sb.append("(Không có dữ liệu buổi học)\n");
            sb.append("--------------------------------------------------------------------------------\n");
        }

        sb.append("================================================================================\n");
        sb.append("Ghi chú: Các buổi học trên có thể khôi phục trong ứng dụng trước ngày xóa vĩnh viễn.\n");
        sb.append("================================================================================\n");

        return sb.toString();
    }

    public static void writeToStream(OutputStream outputStream, List<Schedule> schedules) throws Exception {
        String content = buildLogContent(schedules);
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        }
    }

    public static String generateDefaultFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US);
        return "ThoiKhoaBieu_LichDaDon_" + sdf.format(new Date()) + ".txt";
    }
}
