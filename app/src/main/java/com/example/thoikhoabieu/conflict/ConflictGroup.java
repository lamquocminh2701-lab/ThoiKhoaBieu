package com.example.thoikhoabieu.conflict;

import com.example.thoikhoabieu.model.Schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Một nhóm các buổi học bị giao nhau / trùng giờ trong cùng một ngày.
 * Nếu schedules.size() == 1, đây là một buổi học đơn lẻ không bị trùng.
 * Nếu schedules.size() > 1, đây là một nhóm trùng giờ.
 */
public class ConflictGroup {
    public final String date;
    public int startMin;
    public int endMin;
    public final List<Schedule> schedules = new ArrayList<>();

    public ConflictGroup(String date) {
        this.date = date;
    }

    public void add(Schedule s) {
        schedules.add(s);
        if (schedules.size() == 1) {
            startMin = s.startMin;
            endMin = s.endMin;
        } else {
            startMin = Math.min(startMin, s.startMin);
            endMin = Math.max(endMin, s.endMin);
        }
    }

    public boolean isConflict() {
        return schedules.size() > 1;
    }

    /**
     * Lấy buổi học được ưu tiên hiển thị.
     * Quy tắc:
     * 1. Buổi học có isPriority == true.
     * 2. Nếu chưa có hoặc nhiều hơn 1, chọn buổi học đầu tiên (startMin nhỏ nhất).
     */
    public Schedule getPrioritySchedule() {
        if (schedules.isEmpty()) return null;
        for (Schedule s : schedules) {
            if (s.isPriority) return s;
        }
        // Mặc định chọn buổi đầu tiên
        return schedules.get(0);
    }

    public List<Schedule> getNonPrioritySchedules() {
        Schedule priority = getPrioritySchedule();
        List<Schedule> list = new ArrayList<>();
        for (Schedule s : schedules) {
            if (s != priority) list.add(s);
        }
        return list;
    }

    public List<String> getAllIds() {
        List<String> ids = new ArrayList<>();
        for (Schedule s : schedules) {
            if (s.id != null) ids.add(s.id);
        }
        return ids;
    }
}
