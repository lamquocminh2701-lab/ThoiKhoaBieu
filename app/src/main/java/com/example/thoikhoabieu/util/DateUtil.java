package com.example.thoikhoabieu.util;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

public final class DateUtil {
    /** Khung giờ dùng để gợi ý khoảng trống: 06:00 – 22:00. */
    public static final int DAY_START = 6 * 60;
    public static final int DAY_END = 22 * 60;
    /** Khoảng trống tối thiểu để hiển thị (phút). */
    public static final int MIN_GAP = 15;
    public static final int PERIOD_MIN = 45;
    public static final int DEFAULT_DURATION = 90;

    private static final String[] DOW_FULL = {"", "Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
    private static final String[] DOW_SHORT = {"", "CN", "T2", "T3", "T4", "T5", "T6", "T7"};

    private DateUtil() {
    }

    public static Calendar today() {
        Calendar c = Calendar.getInstance();
        zero(c);
        return c;
    }

    public static void zero(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    public static String iso(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static Calendar parse(String iso) {
        String[] p = iso.split("-");
        return new GregorianCalendar(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
    }

    public static String dmy(Calendar c) {
        return String.format(Locale.US, "%02d/%02d/%04d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    public static String dm(Calendar c) {
        return String.format(Locale.US, "%02d/%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1);
    }

    public static String hm(int min) {
        return String.format(Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    public static int nowMin() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static long toMillis(String iso, int min) {
        Calendar c = parse(iso);
        c.add(Calendar.MINUTE, min);
        return c.getTimeInMillis();
    }

    /** Thứ Hai của tuần chứa ngày c (đã đưa về 00:00). */
    public static Calendar mondayOf(Calendar c) {
        Calendar r = (Calendar) c.clone();
        zero(r);
        int diff = (r.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        r.add(Calendar.DAY_OF_YEAR, -diff);
        return r;
    }

    public static Calendar addDays(Calendar c, int n) {
        Calendar r = (Calendar) c.clone();
        r.add(Calendar.DAY_OF_YEAR, n);
        return r;
    }

    public static String dowFull(Calendar c) {
        return DOW_FULL[c.get(Calendar.DAY_OF_WEEK)];
    }

    public static String dowShort(Calendar c) {
        return DOW_SHORT[c.get(Calendar.DAY_OF_WEEK)];
    }

    public static boolean isToday(Calendar c) {
        return iso(c).equals(iso(Calendar.getInstance()));
    }

    /** 90 phút = 2 tiết (mỗi tiết 45 phút). */
    public static int periods(int startMin, int endMin) {
        return Math.max(1, Math.round((endMin - startMin) / (float) PERIOD_MIN));
    }

    /** Bỏ dấu + chữ thường, dùng để so khớp tên môn. */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
