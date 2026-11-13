package com.example.thoikhoabieu.conflict;

import com.example.thoikhoabieu.model.Schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thuật toán Connected Components phân nhóm các khoảng thời gian bị trùng nhau:
 * Hai khoảng [startA, endA) và [startB, endB) trùng nhau khi:
 * startA < endB && startB < endA.
 * Nhóm liên thông: Nếu A trùng B và B trùng C thì {A, B, C} thuộc cùng 1 ConflictGroup.
 */
public final class ConflictDetector {
    private ConflictDetector() {
    }

    /**
     * Phân nhóm danh sách các buổi học của một ngày thành các ConflictGroup.
     */
    public static List<ConflictGroup> groupConflicts(List<Schedule> daySchedules) {
        List<ConflictGroup> groups = new ArrayList<>();
        if (daySchedules == null || daySchedules.isEmpty()) return groups;

        // Sắp xếp các buổi học theo startMin tăng dần, rồi đến endMin
        List<Schedule> sorted = new ArrayList<>(daySchedules);
        Collections.sort(sorted, (a, b) -> {
            int cmp = Integer.compare(a.startMin, b.startMin);
            if (cmp != 0) return cmp;
            return Integer.compare(a.endMin, b.endMin);
        });

        String date = sorted.get(0).date;
        ConflictGroup currentGroup = new ConflictGroup(date);
        currentGroup.add(sorted.get(0));
        int maxEnd = sorted.get(0).endMin;

        for (int i = 1; i < sorted.size(); i++) {
            Schedule s = sorted.get(i);
            // Hai buổi giao nhau khi start < maxEnd của nhóm hiện tại
            if (s.startMin < maxEnd) {
                currentGroup.add(s);
                maxEnd = Math.max(maxEnd, s.endMin);
            } else {
                groups.add(currentGroup);
                currentGroup = new ConflictGroup(date);
                currentGroup.add(s);
                maxEnd = s.endMin;
            }
        }
        groups.add(currentGroup);
        return groups;
    }

    /**
     * Lọc danh sách lịch học (có thể kéo dài nhiều ngày), chỉ giữ lại lịch ưu tiên
     * của mỗi nhóm trùng giờ và các lịch không trùng, dùng cho AlarmManager và xem nhanh.
     */
    public static List<Schedule> filterPriorityOnly(List<Schedule> all) {
        if (all == null || all.isEmpty()) return Collections.emptyList();

        // Nhóm theo ngày
        Map<String, List<Schedule>> byDate = new HashMap<>();
        for (Schedule s : all) {
            List<Schedule> l = byDate.get(s.date);
            if (l == null) {
                l = new ArrayList<>();
                byDate.put(s.date, l);
            }
            l.add(s);
        }

        List<Schedule> out = new ArrayList<>();
        for (Map.Entry<String, List<Schedule>> entry : byDate.entrySet()) {
            List<ConflictGroup> groups = groupConflicts(entry.getValue());
            for (ConflictGroup g : groups) {
                out.add(g.getPrioritySchedule());
            }
        }
        // Sắp xếp lại theo date ASC, startMin ASC
        Collections.sort(out, (a, b) -> {
            int cmp = a.date.compareTo(b.date);
            if (cmp != 0) return cmp;
            return Integer.compare(a.startMin, b.startMin);
        });
        return out;
    }

    public static boolean hasOverlap(Schedule a, Schedule b) {
        if (a == null || b == null || !a.date.equals(b.date)) return false;
        return a.startMin < b.endMin && b.startMin < a.endMin;
    }
}
