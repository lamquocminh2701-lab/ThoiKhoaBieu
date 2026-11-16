
package com.example.thoikhoabieu.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private Prefs() {
    }

    // Lấy SharedPreferences
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    // ==================== NHẮC NHỞ ====================

    public static boolean reminderEnabled(Context c) {
        return sp(c).getBoolean("reminder", true);
    }

    public static void setReminderEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("reminder", v).apply();
    }

    public static int leadMinutes(Context c) {
        return sp(c).getInt("lead", 15);
    }

    public static void setLeadMinutes(Context c, int v) {
        sp(c).edit().putInt("lead", v).apply();
    }

    // ==================== ÂM THANH ====================

    public static boolean sound(Context c) {
        return sp(c).getBoolean("sound", true);
    }

    public static void setSound(Context c, boolean v) {
        sp(c).edit().putBoolean("sound", v).apply();
    }

    public static boolean vibrate(Context c) {
        return sp(c).getBoolean("vibrate", false);
    }

    public static void setVibrate(Context c, boolean v) {
        sp(c).edit().putBoolean("vibrate", v).apply();
    }

    // ==================== KHUNG GIỜ YÊN LẶNG ====================

    public static boolean quietEnabled(Context c) {
        return sp(c).getBoolean("quiet", true);
    }

    public static void setQuietEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("quiet", v).apply();
    }

    public static int quietStart(Context c) {
        return sp(c).getInt("quiet_start", 22 * 60);
    }

    public static int quietEnd(Context c) {
        return sp(c).getInt("quiet_end", 7 * 60);
    }

    public static void setQuietRange(Context c, int start, int end) {
        sp(c).edit()
                .putInt("quiet_start", start)
                .putInt("quiet_end", end)
                .apply();
    }

    // ==================== HỌC KỲ ====================

    public static String semesterKey(Context c) {
        return sp(c).getString("semester", "");
    }

    public static void setSemesterKey(Context c, String k) {
        sp(c).edit().putString("semester", k).apply();
    }

    // ==================== DỌN DẸP LỊCH HỌC ====================

    public static boolean cleanupPromptEnabled(Context c) {
        return sp(c).getBoolean("cleanup_prompt", true);
    }

    public static void setCleanupPromptEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("cleanup_prompt", v).apply();
    }

    // Thời gian đề xuất dọn dẹp: tối thiểu 15 ngày, tối đa 180 ngày
    public static int retentionDays(Context c) {
        return Math.max(
                15,
                Math.min(180, sp(c).getInt("retention_days", 180))
        );
    }

    public static void setRetentionDays(Context c, int days) {
        sp(c).edit()
                .putInt("retention_days", Math.max(15, Math.min(180, days)))
                .apply();
    }

    // ==================== THỜI GIAN BỎ QUA NHẮC DỌN ====================

    public static long lastCleanupDismissedTime(Context c) {
        return sp(c).getLong("last_cleanup_dismissed", 0L);
    }

    public static void setLastCleanupDismissedTime(Context c, long millis) {
        sp(c).edit()
                .putLong("last_cleanup_dismissed", millis)
                .apply();
    }
}
