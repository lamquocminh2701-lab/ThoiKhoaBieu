package com.example.thoikhoabieu.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.notify.ReminderScheduler;
import com.example.thoikhoabieu.util.DateUtil;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nguồn dữ liệu duy nhất của app: SQLite trên máy, hoạt động hoàn toàn offline.
 *
 * <ul>
 *   <li>Đọc (getByDate, getRange, findConflicts, freeSlots...) trả kết quả ngay.</li>
 *   <li>Ghi (insert/update/delete) chạy đồng bộ và gọi callback ngay; dữ liệu ít nên rất nhanh.</li>
 *   <li>Thêm/sửa luôn kiểm tra trùng lịch (kể cả {@link #GAP} phút nghỉ) trong cùng một transaction,
 *       nên dữ liệu trùng không thể vào database.</li>
 * </ul>
 */
public class ScheduleRepository {
    /** Số phút nghỉ tối thiểu giữa hai buổi học. */
    public static final int GAP = 1;

    public static class Result {
        public static final int OK = 1;
        public static final int ERROR = -1;
        public static final int CONFLICT = -2;

        public final int code;
        public final String message;
        public final Schedule schedule;
        public final List<Schedule> conflicts;

        Result(int code, String message, Schedule schedule, List<Schedule> conflicts) {
            this.code = code;
            this.message = message;
            this.schedule = schedule;
            this.conflicts = conflicts == null ? Collections.<Schedule>emptyList() : conflicts;
        }

        public boolean ok() {
            return code == OK;
        }
    }

    public interface Callback {
        void onResult(Result r);
    }

    private static final String T = "schedule";
    private static final String[] COLS = {"id", "subject", "class_name", "room", "date", "start_min", "end_min", "note", "status"};
    private static ScheduleRepository instance;

    public static synchronized ScheduleRepository get(Context ctx) {
        if (instance == null) instance = new ScheduleRepository(ctx.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final Helper helper;

    private ScheduleRepository(Context ctx) {
        this.ctx = ctx;
        this.helper = new Helper(ctx);
        importLegacyCache();
    }

    // ================================================================ SQLite

    private static class Helper extends SQLiteOpenHelper {
        // Phiên bản 1: bản SQLite đầu tiên (chưa có cột status). Phiên bản 2: thêm cột status.
        Helper(Context c) {
            super(c, "timetable.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + T + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "subject TEXT NOT NULL,"
                    + "class_name TEXT NOT NULL,"
                    + "room TEXT NOT NULL,"
                    + "date TEXT NOT NULL,"
                    + "start_min INTEGER NOT NULL,"
                    + "end_min INTEGER NOT NULL,"
                    + "note TEXT,"
                    + "status TEXT NOT NULL DEFAULT 'DANG_HOC',"
                    + "CHECK (end_min > start_min))");
            db.execSQL("CREATE INDEX idx_schedule_date ON " + T + " (date, start_min)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Giữ nguyên dữ liệu cũ, chỉ thêm cột trạng thái môn học.
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + T + " ADD COLUMN status TEXT NOT NULL DEFAULT 'DANG_HOC'");
            }
        }
    }

    private Schedule map(Cursor c) {
        Schedule s = new Schedule();
        s.id = String.valueOf(c.getLong(0));
        s.subject = c.getString(1);
        s.className = c.getString(2);
        s.room = c.getString(3);
        s.date = c.getString(4);
        s.startMin = c.getInt(5);
        s.endMin = c.getInt(6);
        s.note = c.getString(7);
        s.status = c.getString(8) == null ? Schedule.DANG_HOC : c.getString(8);
        return s;
    }

    private List<Schedule> query(SQLiteDatabase db, String where, String[] args, String order) {
        List<Schedule> out = new ArrayList<>();
        try (Cursor c = db.query(T, COLS, where, args, null, null, order)) {
            while (c.moveToNext()) out.add(map(c));
        }
        return out;
    }

    private ContentValues values(Schedule s) {
        ContentValues v = new ContentValues();
        v.put("subject", s.subject);
        v.put("class_name", s.className);
        v.put("room", s.room);
        v.put("date", s.date);
        v.put("start_min", s.startMin);
        v.put("end_min", s.endMin);
        v.put("note", s.note);
        v.put("status", s.status == null ? Schedule.DANG_HOC : s.status);
        return v;
    }

    // ================================================================ đọc

    public Schedule getById(String id) {
        if (id == null) return null;
        List<Schedule> l = query(helper.getReadableDatabase(), "id=?", new String[]{id}, null);
        return l.isEmpty() ? null : l.get(0);
    }

    public List<Schedule> getByDate(String date) {
        return query(helper.getReadableDatabase(), "date=?", new String[]{date}, "start_min ASC");
    }

    /** Các buổi học trong [from, to] (yyyy-MM-dd, so sánh chuỗi), sắp theo ngày rồi giờ. */
    public List<Schedule> getRange(String from, String to) {
        return query(helper.getReadableDatabase(), "date>=? AND date<=?", new String[]{from, to}, "date ASC, start_min ASC");
    }

    /** Ngày nhỏ nhất sau {@code iso} có lịch học, hoặc null. */
    public String getNextDateAfter(String iso) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT MIN(date) FROM " + T + " WHERE date>?", new String[]{iso})) {
            if (c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        }
        return null;
    }

    private static boolean same(String a, String b) {
        return a != null && a.equals(b);
    }

    private List<Schedule> conflicts(SQLiteDatabase db, String date, int start, int end, String excludeId) {
        // Hai khoảng [a,b) và [c,d) giao nhau khi a<d và b>c.
        return query(db, "date=? AND start_min<? AND end_min>? AND id<>?",
                new String[]{date, String.valueOf(end), String.valueOf(start), excludeId == null ? "-1" : excludeId},
                "start_min ASC");
    }

    /** Các buổi giao với khoảng [start, end) trong ngày (không tính khoảng nghỉ). */
    public List<Schedule> findConflicts(String date, int start, int end, String excludeId) {
        return conflicts(helper.getReadableDatabase(), date, start, end, excludeId);
    }

    /** Các khoảng trống (>= 15 phút) trong khung 06:00–22:00 của một ngày. */
    public List<int[]> freeSlots(String date, String excludeId) {
        List<int[]> out = new ArrayList<>();
        int cur = DateUtil.DAY_START;
        for (Schedule s : getByDate(date)) {
            if (same(excludeId, s.id)) continue;
            if (s.startMin - cur >= DateUtil.MIN_GAP) out.add(new int[]{cur, Math.min(s.startMin, DateUtil.DAY_END)});
            cur = Math.max(cur, s.endMin);
        }
        if (DateUtil.DAY_END - cur >= DateUtil.MIN_GAP) out.add(new int[]{cur, DateUtil.DAY_END});
        return out;
    }

    /** Giờ bắt đầu sớm nhất (bước 15 phút, từ fromMin) đủ {@code duration} phút không trùng; -1 nếu không có. */
    public int firstFreeStart(String date, int duration, int fromMin, String excludeId) {
        List<Schedule> day = getByDate(date);
        int start = ((Math.max(fromMin, 0) + 14) / 15) * 15;
        while (start + duration <= DateUtil.DAY_END) {
            boolean ok = true;
            for (Schedule s : day) {
                if (same(excludeId, s.id)) continue;
                if (s.startMin < start + duration && s.endMin > start) {
                    ok = false;
                    start = ((s.endMin + 14) / 15) * 15;
                    break;
                }
            }
            if (ok) return start;
        }
        return -1;
    }

    // ================================================================ ghi

    public void insert(Schedule s, Callback cb) {
        finish(write(s, null), cb);
    }

    public void update(Schedule s, Callback cb) {
        finish(write(s, s.id), cb);
    }

    public void delete(String id, Callback cb) {
        Result r;
        try {
            helper.getWritableDatabase().delete(T, "id=?", new String[]{id});
            r = new Result(Result.OK, null, null, null);
        } catch (Exception e) {
            r = new Result(Result.ERROR, "Không thể xóa lịch học", null, null);
        }
        finish(r, cb);
    }

    /** Ghi trong một transaction: kiểm tra trùng lịch (có GAP phút nghỉ) rồi mới ghi. */
    private Result write(Schedule s, String id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            List<Schedule> conf = conflicts(db, s.date, Math.max(0, s.startMin - GAP), s.endMin + GAP, id);
            if (!conf.isEmpty()) {
                return new Result(Result.CONFLICT, "Trùng lịch học hoặc không đủ thời gian nghỉ giữa các buổi", null, conf);
            }
            if (id == null) {
                long rowId = db.insert(T, null, values(s));
                if (rowId < 0) return new Result(Result.ERROR, "Không thể lưu lịch học", null, null);
                s.id = String.valueOf(rowId);
            } else {
                int n = db.update(T, values(s), "id=?", new String[]{id});
                if (n <= 0) return new Result(Result.ERROR, "Không tìm thấy buổi học để cập nhật", null, null);
            }
            db.setTransactionSuccessful();
            return new Result(Result.OK, null, s, null);
        } catch (Exception e) {
            return new Result(Result.ERROR, "Không thể lưu lịch học", null, null);
        } finally {
            db.endTransaction();
        }
    }

    private void finish(Result r, Callback cb) {
        if (r.ok()) ReminderScheduler.scheduleNext(ctx);
        if (cb != null) cb.onResult(r);
    }

    // ================================================================ nhập dữ liệu từ bản MongoDB

    /**
     * Nếu máy từng chạy bản dùng MongoDB, file schedules_cache.json còn giữ một bản sao toàn bộ lịch học.
     * Khi bảng còn trống thì nhập các buổi đó vào SQLite rồi đổi tên file để không nhập lại.
     */
    private void importLegacyCache() {
        File f = new File(ctx.getFilesDir(), "schedules_cache.json");
        if (!f.exists()) return;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T, null)) {
                if (c.moveToFirst() && c.getInt(0) > 0) return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            JSONArray arr = new JSONArray(out.toString("UTF-8"));
            db.beginTransaction();
            try {
                for (int i = 0; i < arr.length(); i++) db.insert(T, null, values(Schedule.fromJson(arr.getJSONObject(i))));
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(new File(ctx.getFilesDir(), "schedules_cache.json.imported"));
        } catch (Exception ignored) {
            // không nhập được thì bỏ qua, app vẫn dùng bình thường với bảng trống
        }
    }
}
