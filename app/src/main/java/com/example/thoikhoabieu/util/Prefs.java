package com.example.thoikhoabieu.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static boolean reminderEnabled(Context c) { return sp(c).getBoolean("reminder", true); }
    public static void setReminderEnabled(Context c, boolean v) { sp(c).edit().putBoolean("reminder", v).apply(); }

    public static int leadMinutes(Context c) { return sp(c).getInt("lead", 15); }
    public static void setLeadMinutes(Context c, int v) { sp(c).edit().putInt("lead", v).apply(); }

    public static boolean sound(Context c) { return sp(c).getBoolean("sound", true); }
    public static void setSound(Context c, boolean v) { sp(c).edit().putBoolean("sound", v).apply(); }

    public static boolean vibrate(Context c) { return sp(c).getBoolean("vibrate", false); }
    public static void setVibrate(Context c, boolean v) { sp(c).edit().putBoolean("vibrate", v).apply(); }

    public static boolean quietEnabled(Context c) { return sp(c).getBoolean("quiet", true); }
    public static void setQuietEnabled(Context c, boolean v) { sp(c).edit().putBoolean("quiet", v).apply(); }

    public static int quietStart(Context c) { return sp(c).getInt("quiet_start", 22 * 60); }
    public static int quietEnd(Context c) { return sp(c).getInt("quiet_end", 7 * 60); }
    public static void setQuietRange(Context c, int start, int end) {
        sp(c).edit().putInt("quiet_start", start).putInt("quiet_end", end).apply();
    }

    public static String semesterKey(Context c) { return sp(c).getString("semester", ""); }
    public static void setSemesterKey(Context c, String k) { sp(c).edit().putString("semester", k).apply(); }
}
