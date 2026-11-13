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
import java.util.Calendar;
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
    private static final String[] COLS = {
            "id", "subject", "class_name", "room", "date",
            "start_min", "end_min", "note", "status",
            "trashed_at", "purge_after", "is_priority"
    };
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
        // Phiên bản 1: bản SQLite đầu tiên.
        // Phiên bản 2: thêm cột status.
        // Phiên bản 3: thêm trashed_at, purge_after (thùng rác), is_priority (lịch ưu tiên).
        Helper(Context c) {
            super(c, "timetable.db", null, 3);
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
                    + "trashed_at INTEGER,"
                    + "purge_after INTEGER,"
                    + "is_priority INTEGER NOT NULL DEFAULT 0,"
                    + "CHECK (end_min > start_min))");
            db.execSQL("CREATE INDEX idx_schedule_date ON " + T + " (date, start_min)");
            db.execSQL("CREATE INDEX idx_schedule_trash ON " + T + " (trashed_at, purge_after)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Giữ nguyên dữ liệu cũ, nâng cấp schema an toàn
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + T + " ADD COLUMN status TEXT NOT NULL DEFAULT 'DANG_HOC'");
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE " + T + " ADD COLUMN trashed_at INTEGER");
                db.execSQL("ALTER TABLE " + T + " ADD COLUMN purge_after INTEGER");
                db.execSQL("ALTER TABLE " + T + " ADD COLUMN is_priority INTEGER NOT NULL DEFAULT 0");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_trash ON " + T + " (trashed_at, purge_after)");
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
        if (c.getColumnCount() > 9) {
            s.trashedAt = c.isNull(9) ? null : c.getLong(9);
            s.purgeAfter = c.isNull(10) ? null : c.getLong(10);
            s.isPriority = c.getInt(11) == 1;
        }
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
        if (s.trashedAt != null) v.put("trashed_at", s.trashedAt);
        else v.putNull("trashed_at");
        if (s.purgeAfter != null) v.put("purge_after", s.purgeAfter);
        else v.putNull("purge_after");
        v.put("is_priority", s.isPriority ? 1 : 0);
        return v;
    }

    // ================================================================ đọc

    public Schedule getById(String id) {
        if (id == null) return null;
        List<Schedule> l = query(helper.getReadableDatabase(), "id=?", new String[]{id}, null);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Lấy tất cả lịch học đang hoạt động trong ngày (loại trừ thùng rác). */
    public List<Schedule> getByDate(String date) {
        return query(helper.getReadableDatabase(), "date=? AND trashed_at IS NULL", new String[]{date}, "start_min ASC");
    }

    /** Các buổi học đang hoạt động trong [from, to] (loại trừ thùng rác), sắp theo ngày rồi giờ. */
    public List<Schedule> getRange(String from, String to) {
        return query(helper.getReadableDatabase(), "date>=? AND date<=? AND trashed_at IS NULL", new String[]{from, to}, "date ASC, start_min ASC");
    }

    /** Ngày nhỏ nhất sau {@code iso} có lịch học đang hoạt động, hoặc null. */
    public String getNextDateAfter(String iso) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT MIN(date) FROM " + T + " WHERE date>? AND trashed_at IS NULL", new String[]{iso})) {
            if (c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        }
        return null;
    }

    private static boolean same(String a, String b) {
        return a != null && a.equals(b);
    }

    private List<Schedule> conflicts(SQLiteDatabase db, String date, int start, int end, String excludeId) {
        // Hai khoảng [a,b) và [c,d) giao nhau khi a<d và b>c. Chỉ xét lịch đang hoạt động.
        return query(db, "date=? AND start_min<? AND end_min>? AND id<>? AND trashed_at IS NULL",
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
        insert(s, false, cb);
    }

    public void insert(Schedule s, boolean allowConflict, Callback cb) {
        finish(write(s, null, allowConflict), cb);
    }

    public void update(Schedule s, Callback cb) {
        update(s, false, cb);
    }

    public void update(Schedule s, boolean allowConflict, Callback cb) {
        finish(write(s, s.id, allowConflict), cb);
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

    // ================================================================ Thùng rác & Lịch ưu tiên

    /**
     * Chuyển danh sách lịch học vào thùng rác (soft delete).
     * Đặt trashed_at = now, purge_after = now + 6 ngày.
     */
    public void moveToTrash(List<String> ids, Callback cb) {
        if (ids == null || ids.isEmpty()) {
            if (cb != null) cb.onResult(new Result(Result.OK, null, null, null));
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        long purgeAfter = now + 6 * 86_400_000L; // 6 ngày lưu trữ
        ContentValues cv = new ContentValues();
        cv.put("trashed_at", now);
        cv.put("purge_after", purgeAfter);
        cv.put("is_priority", 0);

        db.beginTransaction();
        Result r;
        try {
            for (String id : ids) {
                db.update(T, cv, "id=?", new String[]{id});
            }
            db.setTransactionSuccessful();
            r = new Result(Result.OK, "Đã chuyển " + ids.size() + " buổi học vào thùng rác.", null, null);
        } catch (Exception e) {
            r = new Result(Result.ERROR, "Lỗi khi chuyển vào thùng rác: " + e.getMessage(), null, null);
        } finally {
            db.endTransaction();
        }
        finish(r, cb);
    }

    /**
     * Khôi phục một buổi học từ thùng rác về thời khóa biểu bình thường.
     */
    public void restoreFromTrash(String id, Callback cb) {
        if (id == null) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.putNull("trashed_at");
        cv.putNull("purge_after");

        Result r;
        try {
            int n = db.update(T, cv, "id=?", new String[]{id});
            if (n > 0) {
                r = new Result(Result.OK, "Đã khôi phục buổi học thành công.", null, null);
            } else {
                r = new Result(Result.ERROR, "Không tìm thấy buổi học để khôi phục.", null, null);
            }
        } catch (Exception e) {
            r = new Result(Result.ERROR, "Lỗi khi khôi phục: " + e.getMessage(), null, null);
        }
        finish(r, cb);
    }

    /**
     * Khôi phục tất cả buổi học trong thùng rác.
     */
    public void restoreAllTrash(Callback cb) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.putNull("trashed_at");
        cv.putNull("purge_after");

        Result r;
        try {
            int n = db.update(T, cv, "trashed_at IS NOT NULL", null);
            r = new Result(Result.OK, "Đã khôi phục " + n + " buổi học.", null, null);
        } catch (Exception e) {
            r = new Result(Result.ERROR, "Lỗi khi khôi phục tất cả: " + e.getMessage(), null, null);
        }
        finish(r, cb);
    }

    /**
     * Lấy danh sách các buổi học đang nằm trong thùng rác, xếp theo thời gian đưa vào giảm dần.
     */
    public List<Schedule> getTrashSchedules() {
        return query(helper.getReadableDatabase(), "trashed_at IS NOT NULL", null, "trashed_at DESC");
    }

    /**
     * Đếm số lượng buổi học trong thùng rác.
     */
    public int getTrashCount() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + T + " WHERE trashed_at IS NOT NULL", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    /**
     * Lấy danh sách các buổi học đã kết thúc từ retentionDays ngày trước trở về trước,
     * chưa nằm trong thùng rác và không phải trạng thái TAM_NGHI.
     */
    public List<Schedule> getEligibleOldSchedules(int retentionDays) {
        long now = System.currentTimeMillis();
        long thresholdMillis = now - retentionDays * 86_400_000L;
        Calendar threshCal = Calendar.getInstance();
        threshCal.setTimeInMillis(thresholdMillis);
        String threshIso = DateUtil.iso(threshCal);

        // Lấy tất cả lịch học có date <= threshIso, trashed_at IS NULL, status <> 'TAM_NGHI'
        List<Schedule> candidates = query(helper.getReadableDatabase(),
                "date<=? AND trashed_at IS NULL AND status<>?",
                new String[]{threshIso, Schedule.TAM_NGHI},
                "date ASC, start_min ASC");

        List<Schedule> eligible = new ArrayList<>();
        for (Schedule s : candidates) {
            long endEpoch = DateUtil.toMillis(s.date, s.endMin);
            if (endEpoch <= thresholdMillis) {
                eligible.add(s);
            }
        }
        return eligible;
    }

    /**
     * Xóa vĩnh viễn (hard delete) các bản ghi trong thùng rác đã quá 6 ngày (purge_after <= now).
     * Chạy an toàn trong transaction.
     */
    public int purgeExpiredTrash() {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        int deleted = 0;
        db.beginTransaction();
        try {
            deleted = db.delete(T, "trashed_at IS NOT NULL AND purge_after IS NOT NULL AND purge_after <= ?",
                    new String[]{String.valueOf(now)});
            db.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            db.endTransaction();
        }
        return deleted;
    }

    /**
     * Thiết lập một buổi học làm lịch ưu tiên hiển thị trong nhóm trùng giờ.
     * Đặt is_priority = 0 cho tất cả ID trong nhóm, và is_priority = 1 cho selectedId.
     */
    public void setPriority(String selectedId, List<String> groupScheduleIds, Callback cb) {
        if (selectedId == null || groupScheduleIds == null || groupScheduleIds.isEmpty()) {
            if (cb != null) cb.onResult(new Result(Result.OK, null, null, null));
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        Result r;
        try {
            ContentValues resetCv = new ContentValues();
            resetCv.put("is_priority", 0);
            for (String id : groupScheduleIds) {
                db.update(T, resetCv, "id=?", new String[]{id});
            }

            ContentValues setCv = new ContentValues();
            setCv.put("is_priority", 1);
            db.update(T, setCv, "id=?", new String[]{selectedId});

            db.setTransactionSuccessful();
            r = new Result(Result.OK, "Đã cập nhật lịch ưu tiên.", null, null);
        } catch (Exception e) {
            r = new Result(Result.ERROR, "Lỗi khi cập nhật ưu tiên: " + e.getMessage(), null, null);
        } finally {
            db.endTransaction();
        }
        finish(r, cb);
    }

    /** Kiểm tra xem đã có buổi học nào giống hệt (cùng ngày, giờ, môn, phòng, lớp) trong database chưa. */
    public boolean isExactDuplicate(Schedule s) {
        if (s == null) return false;
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(T, new String[]{"id"},
                "date=? AND start_min=? AND end_min=? AND subject=? AND room=? AND class_name=? AND trashed_at IS NULL",
                new String[]{s.date, String.valueOf(s.startMin), String.valueOf(s.endMin), s.subject, s.room == null ? "" : s.room, s.className == null ? "" : s.className},
                null, null, null)) {
            return c.moveToFirst();
        }
    }

    /**
     * Nhập hàng loạt các buổi học trong một Transaction duy nhất.
     * Tự động rollback nếu xảy ra ngoại lệ, đảm bảo an toàn toàn vẹn dữ liệu.
     * Khi thành công, tự động kích hoạt ReminderScheduler để cập nhật báo thức.
     */
    public void importBatch(List<Schedule> list, Callback cb) {
        if (list == null || list.isEmpty()) {
            if (cb != null) cb.onResult(new Result(Result.OK, "Không có dữ liệu cần nhập", null, null));
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        Result r;
        try {
            for (Schedule s : list) {
                long rowId = db.insert(T, null, values(s));
                if (rowId < 0) {
                    throw new Exception("Lỗi khi chèn buổi học \"" + s.subject + "\" vào cơ sở dữ liệu.");
                }
                s.id = String.valueOf(rowId);
            }
            db.setTransactionSuccessful();
            r = new Result(Result.OK, "Đã nhập thành công " + list.size() + " buổi học.", null, null);
        } catch (Exception e) {
            r = new Result(Result.ERROR, "Lỗi khi nhập dữ liệu hàng loạt: " + e.getMessage(), null, null);
        } finally {
            db.endTransaction();
        }
        finish(r, cb);
    }

    /** Ghi trong một transaction: nếu !allowConflict, kiểm tra trùng lịch (có GAP phút nghỉ) rồi mới ghi. */
    private Result write(Schedule s, String id, boolean allowConflict) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            List<Schedule> conf = conflicts(db, s.date, Math.max(0, s.startMin - GAP), s.endMin + GAP, id);
            if (!conf.isEmpty() && !allowConflict) {
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
            return new Result(Result.OK, null, s, conf);
        } catch (Exception e) {
            return new Result(Result.ERROR, "Không thể lưu lịch học: " + e.getMessage(), null, null);
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
