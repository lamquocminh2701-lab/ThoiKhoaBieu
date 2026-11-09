package com.example.thoikhoabieu.model;

import com.example.thoikhoabieu.util.DateUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Học kỳ 18 tuần. Học kỳ I bắt đầu tuần chứa 24/08, học kỳ II bắt đầu tuần chứa 11/01.
 */
public class Semester {
    public static final int WEEKS = 18;

    public final int year;
    public final int term;
    public final String startIso;
    public final String endIso;

    private Semester(int year, int term) {
        this.year = year;
        this.term = term;
        Calendar base = term == 1
                ? new GregorianCalendar(year, Calendar.AUGUST, 24)
                : new GregorianCalendar(year + 1, Calendar.JANUARY, 11);
        Calendar start = DateUtil.mondayOf(base);
        startIso = DateUtil.iso(start);
        endIso = DateUtil.iso(DateUtil.addDays(start, WEEKS * 7 - 1));
    }

    public static Semester of(int year, int term) {
        return new Semester(year, term);
    }

    public String key() {
        return year + "-" + term;
    }

    public String label() {
        return "Học kỳ " + (term == 1 ? "I" : "II") + " · " + year + "–" + (year + 1);
    }

    public static Semester forDate(Calendar c) {
        int y = c.get(Calendar.YEAR);
        String iso = DateUtil.iso(c);
        if (iso.compareTo(of(y, 1).startIso) >= 0) return of(y, 1);
        if (iso.compareTo(of(y - 1, 2).startIso) >= 0) return of(y - 1, 2);
        return of(y - 1, 1);
    }

    public static List<Semester> options() {
        Semester cur = forDate(Calendar.getInstance());
        List<Semester> list = new ArrayList<>();
        for (int y = cur.year - 1; y <= cur.year + 1; y++) {
            list.add(of(y, 1));
            list.add(of(y, 2));
        }
        return list;
    }

    public static Semester fromKey(String key) {
        if (key != null && key.matches("\\d{4}-[12]")) {
            String[] p = key.split("-");
            return of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        return null;
    }
}
