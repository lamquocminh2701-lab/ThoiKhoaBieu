package com.example.thoikhoabieu.model;

import com.example.thoikhoabieu.util.DateUtil;

import org.json.JSONException;
import org.json.JSONObject;

/** Một buổi học (một dòng trong bảng "schedule" của SQLite). */
public class Schedule {
    /** Trạng thái môn học. */
    public static final String DANG_HOC = "DANG_HOC";
    public static final String HOAN_THANH = "HOAN_THANH";
    public static final String TAM_NGHI = "TAM_NGHI";
    public static final String[] STATUSES = {DANG_HOC, HOAN_THANH, TAM_NGHI};

    /** Khóa chính (id) của bảng dưới dạng chuỗi; null khi chưa được lưu. */
    public String id;
    public String subject;
    public String className;
    public String room;
    /** yyyy-MM-dd */
    public String date;
    /** Phút trong ngày (0–1439). */
    public int startMin;
    public int endMin;
    public String note;
    public String status = DANG_HOC;

    /** Thời điểm đưa vào thùng rác (epoch millis); null nếu đang hoạt động. */
    public Long trashedAt;
    /** Thời điểm đủ điều kiện xóa vĩnh viễn (epoch millis); null nếu đang hoạt động. */
    public Long purgeAfter;
    /** Cờ ưu tiên hiển thị khi nằm trong nhóm lịch trùng giờ. */
    public boolean isPriority;

    public boolean isTrashed() {
        return trashedAt != null && trashedAt > 0;
    }

    public boolean isPurgeEligible() {
        return isTrashed() && purgeAfter != null && System.currentTimeMillis() >= purgeAfter;
    }

    public String timeLeftText() {
        if (!isTrashed() || purgeAfter == null) return "";
        long diff = purgeAfter - System.currentTimeMillis();
        if (diff <= 0) return "Đã đủ điều kiện xóa vĩnh viễn";
        long days = diff / (24 * 3600 * 1000L);
        long hours = (diff % (24 * 3600 * 1000L)) / (3600 * 1000L);
        if (days > 0) {
            return "Còn " + days + " ngày " + hours + " giờ để khôi phục";
        } else {
            long mins = (diff % (3600 * 1000L)) / (60 * 1000L);
            return "Còn " + hours + " giờ " + mins + " phút để khôi phục";
        }
    }

    public int duration() {
        return endMin - startMin;
    }

    public int periods() {
        return DateUtil.periods(startMin, endMin);
    }

    public String timeRange() {
        return DateUtil.hm(startMin) + "–" + DateUtil.hm(endMin);
    }

    public boolean hasNote() {
        return note != null && !note.trim().isEmpty();
    }

    public static String statusLabel(String status) {
        if (HOAN_THANH.equals(status)) return "Đã hoàn thành";
        if (TAM_NGHI.equals(status)) return "Tạm nghỉ";
        return "Đang học";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("subject", subject);
        o.put("className", className);
        o.put("room", room);
        o.put("date", date);
        o.put("startMin", startMin);
        o.put("endMin", endMin);
        o.put("note", note == null ? "" : note);
        o.put("status", status == null ? DANG_HOC : status);
        if (trashedAt != null) o.put("trashedAt", trashedAt);
        if (purgeAfter != null) o.put("purgeAfter", purgeAfter);
        o.put("isPriority", isPriority);
        return o;
    }

    public static Schedule fromJson(JSONObject o) {
        Schedule s = new Schedule();
        s.id = o.optString("id", null);
        s.subject = o.optString("subject", "");
        s.className = o.optString("className", "");
        s.room = o.optString("room", "");
        s.date = o.optString("date", "");
        s.startMin = o.optInt("startMin");
        s.endMin = o.optInt("endMin");
        s.note = o.optString("note", "");
        s.status = o.optString("status", DANG_HOC);
        if (o.has("trashedAt")) s.trashedAt = o.optLong("trashedAt");
        if (o.has("purgeAfter")) s.purgeAfter = o.optLong("purgeAfter");
        s.isPriority = o.optBoolean("isPriority", false);
        return s;
    }
}
