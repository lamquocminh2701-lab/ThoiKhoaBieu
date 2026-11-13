package com.example.thoikhoabieu;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.model.Semester;
import com.example.thoikhoabieu.util.DateUtil;
import com.example.thoikhoabieu.util.Prefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
public class AddEditActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_END = "end";
    private static final int MAX_MIN = 24 * 60 - 1;
    private static final int GAP = ScheduleRepository.GAP; // phút nghỉ giữa 2 buổi học (kiểm tra cả trong DB)

    public static Intent intentAdd(Context c, String dateIso, int startMin, int endMin) {
        Intent i = new Intent(c, AddEditActivity.class);
        if (dateIso != null) i.putExtra(EXTRA_DATE, dateIso);
        i.putExtra(EXTRA_START, startMin);
        i.putExtra(EXTRA_END, endMin);
        return i;
    }

    public static Intent intentEdit(Context c, String id) {
        return new Intent(c, AddEditActivity.class).putExtra(EXTRA_ID, id);
    }

    private ScheduleRepository db;
    /** _id (ObjectId) của buổi đang sửa; null khi thêm mới. */
    private String editId;
    private Schedule editing;
    private String status = Schedule.DANG_HOC;
    private boolean busy = false;
    private MaterialButton btnSave;
    private TextView stActive, stDone, stPause;
    private String dateIso;
    private int startMin, endMin;

    private TextView tvSubtitle, tvDate, tvStart, tvEnd, tvDuration, tvConflict, tvNoFree, tvReminderTitle, tvReminderSub;
    private EditText etSubject, etClass, etRoom, etNote;
    private LinearLayout chips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit);
        db = ScheduleRepository.get(this);

        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvHint = findViewById(R.id.tv_hint);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvDate = findViewById(R.id.tv_date);
        tvStart = findViewById(R.id.tv_start);
        tvEnd = findViewById(R.id.tv_end);
        tvDuration = findViewById(R.id.tv_duration);
        tvConflict = findViewById(R.id.tv_conflict);
        tvNoFree = findViewById(R.id.tv_no_free);
        tvReminderTitle = findViewById(R.id.tv_reminder_title);
        tvReminderSub = findViewById(R.id.tv_reminder_sub);
        etSubject = findViewById(R.id.et_subject);
        etClass = findViewById(R.id.et_class);
        etRoom = findViewById(R.id.et_room);
        etNote = findViewById(R.id.et_note);
        chips = findViewById(R.id.chips_container);
        btnSave = findViewById(R.id.btn_save);
        stActive = findViewById(R.id.tv_status_active);
        stDone = findViewById(R.id.tv_status_done);
        stPause = findViewById(R.id.tv_status_pause);
        View btnDelete = findViewById(R.id.btn_delete);

        editId = getIntent().getStringExtra(EXTRA_ID);
        if (editId != null) {
            editing = db.getById(editId);
            if (editing == null) {
                finish();
                return;
            }
            tvTitle.setText("Chỉnh sửa lịch học");
            tvHint.setText("Cập nhật thông tin buổi học của bạn.");
            btnSave.setText("Lưu thay đổi");
            btnDelete.setVisibility(View.VISIBLE);
            etSubject.setText(editing.subject);
            etClass.setText(editing.className);
            etRoom.setText(editing.room);
            etNote.setText(editing.note);
            status = editing.status == null ? Schedule.DANG_HOC : editing.status;
            dateIso = editing.date;
            startMin = editing.startMin;
            endMin = editing.endMin;
        } else {
            dateIso = getIntent().getStringExtra(EXTRA_DATE);
            if (dateIso == null) dateIso = DateUtil.iso(Calendar.getInstance());
            int s = getIntent().getIntExtra(EXTRA_START, -1);
            int e = getIntent().getIntExtra(EXTRA_END, -1);
            if (s >= 0 && e > s) {
                fitIntoFreeSlot(s, e);
            } else {
                pickDefaultSlot();
            }
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> confirmDelete());
        btnSave.setOnClickListener(v -> save());
        tvDate.setOnClickListener(v -> pickDate());
        tvStart.setOnClickListener(v -> pickStart());
        tvEnd.setOnClickListener(v -> pickEnd());
        stActive.setOnClickListener(v -> setStatus(Schedule.DANG_HOC));
        stDone.setOnClickListener(v -> setStatus(Schedule.HOAN_THANH));
        stPause.setOnClickListener(v -> setStatus(Schedule.TAM_NGHI));
        renderStatus();

        refresh();
    }

    // ------------------------------------------------- khoảng nghỉ GAP phút

    /** Các buổi bị trùng, tính cả khoảng nghỉ GAP phút ở hai đầu. */
    private List<Schedule> conflicts() {
        return db.findConflicts(dateIso, Math.max(0, startMin - GAP), endMin + GAP, editId);
    }

    /** Khoảng trống đã trừ GAP phút ở phía sát buổi học khác. */
    private List<int[]> freeSlotsGap() {
        List<int[]> out = new ArrayList<>();
        for (int[] r : db.freeSlots(dateIso, editId)) {
            int s = r[0], e = r[1];
            // phía đầu sát một buổi học khác (buổi đó kết thúc đúng lúc s)
            if (s > 0 && !db.findConflicts(dateIso, s - 1, s, editId).isEmpty()) s += GAP;
            // phía cuối sát một buổi học khác (buổi đó bắt đầu đúng lúc e)
            if (!db.findConflicts(dateIso, e, e + 1, editId).isEmpty()) e -= GAP;
            if (e > s) out.add(new int[]{s, e});
        }
        return out;
    }

    /** Giờ bắt đầu trống đầu tiên (có tính GAP) đủ dài dur phút, từ mốc from; -1 nếu không có. */
    private int firstFreeStartGap(int dur, int from) {
        for (int[] r : freeSlotsGap()) {
            int st = Math.max(r[0], from);
            if (r[1] - st >= dur) return st;
        }
        return -1;
    }

    /**
     * Khi mở từ ô "Trống": đặt giờ vào đúng khoảng trống (đã trừ GAP phút nghỉ), tối đa 90 phút.
     */
    private void fitIntoFreeSlot(int s, int e) {
        startMin = s;
        endMin = Math.min(e, s + DateUtil.DEFAULT_DURATION);
        for (int[] r : freeSlotsGap()) {
            if (r[1] > s && r[0] < e) {
                startMin = Math.max(s, r[0]);
                endMin = Math.min(Math.min(e, r[1]), startMin + DateUtil.DEFAULT_DURATION);
                return;
            }
        }
    }

    // ------------------------------------------------------- trạng thái môn học

    private void setStatus(String s) {
        status = s;
        renderStatus();
    }

    private void styleStatusChip(TextView t, String value) {
        boolean sel = value.equals(status);
        boolean pauseChip = Schedule.TAM_NGHI.equals(value);
        t.setBackgroundResource(sel ? (pauseChip ? R.drawable.bg_chip_pause : R.drawable.bg_chip_selected) : R.drawable.bg_chip);
        t.setTextColor(ContextCompat.getColor(this, sel ? (pauseChip ? R.color.danger : R.color.primary) : R.color.text_primary));
        t.setText((sel ? "✓ " : "") + Schedule.statusLabel(value));
        t.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void renderStatus() {
        styleStatusChip(stActive, Schedule.DANG_HOC);
        styleStatusChip(stDone, Schedule.HOAN_THANH);
        styleStatusChip(stPause, Schedule.TAM_NGHI);
    }

    // ------------------------------------------------------------ mặc định

    private void pickDefaultSlot() {
        int from = DateUtil.DAY_START + 60;
        if (dateIso.equals(DateUtil.iso(Calendar.getInstance()))) from = Math.max(from, DateUtil.nowMin());
        int st = firstFreeStartGap(DateUtil.DEFAULT_DURATION, from);
        if (st < 0) st = firstFreeStartGap(DateUtil.DEFAULT_DURATION, DateUtil.DAY_START);
        if (st < 0) st = from;
        startMin = Math.min(st, MAX_MIN - 1);
        endMin = Math.min(startMin + DateUtil.DEFAULT_DURATION, MAX_MIN);
    }

//chọn ngày tháng năm cho ngày học
    private void pickDate() {
        Calendar c = DateUtil.parse(dateIso);
        new DatePickerDialog(this, (view, y, m, d) -> {
            dateIso = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            refresh();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
//chọn giờ bắt đầu
    private void pickStart() {
        new TimePickerDialog(this, (view, h, m) -> {
            int dur = endMin > startMin ? endMin - startMin : DateUtil.DEFAULT_DURATION;
            startMin = Math.min(h * 60 + m, MAX_MIN - 1);
            endMin = Math.min(startMin + dur, MAX_MIN);
            refresh();
        }, startMin / 60, startMin % 60, true).show();
    }
//chọn giờ kết thúc
    private void pickEnd() {
        new TimePickerDialog(this, (view, h, m) -> {
            int e = h * 60 + m;
            if (e <= startMin) {
                Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show();
                return;
            }
            endMin = e;
            refresh();
        }, endMin / 60, endMin % 60, true).show();
    }

    // ---------------------------------------------------------- hiển thị

    private void refresh() {
        Calendar c = DateUtil.parse(dateIso);
        tvDate.setText(DateUtil.dowFull(c) + ", " + DateUtil.dmy(c));
        tvStart.setText(DateUtil.hm(startMin));
        tvEnd.setText(DateUtil.hm(endMin));
        tvSubtitle.setText(editing == null
                ? Semester.forDate(c).label()
                : "Buổi học · " + DateUtil.dowFull(c) + ", " + DateUtil.dmy(c));

        if (endMin > startMin) {
            tvDuration.setText((endMin - startMin) + " phút · " + DateUtil.periods(startMin, endMin) + " tiết học");
            tvDuration.setTextColor(ContextCompat.getColor(this, R.color.primary));
        } else {
            tvDuration.setText("Giờ kết thúc phải sau giờ bắt đầu");
            tvDuration.setTextColor(ContextCompat.getColor(this, R.color.danger));
        }

        List<Schedule> conf = endMin > startMin ? conflicts() : null;
        if (conf != null && !conf.isEmpty()) {
            StringBuilder sb = new StringBuilder("Trùng lịch với: ");
            for (int i = 0; i < conf.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(conf.get(i).subject).append(" (").append(conf.get(i).timeRange()).append(")");
            }
            tvConflict.setText(sb);
            tvConflict.setVisibility(View.VISIBLE);
        } else {
            tvConflict.setVisibility(View.GONE);
        }

        if (Prefs.reminderEnabled(this)) {
            tvReminderTitle.setText("Nhắc trước " + Prefs.leadMinutes(this) + " phút");
            tvReminderSub.setText("Theo tùy chọn trong Cài đặt thông báo.");
        } else {
            tvReminderTitle.setText("Nhắc lịch học đang tắt");
            tvReminderSub.setText("Bật lại trong Cài đặt thông báo.");
        }
        renderChips();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void renderChips() {
        chips.removeAllViews();
        List<int[]> free = freeSlotsGap();
        tvNoFree.setVisibility(free.isEmpty() ? View.VISIBLE : View.GONE);
        for (int[] r : free) {
            TextView t = new TextView(this);
            t.setText(DateUtil.hm(r[0]) + "–" + DateUtil.hm(r[1]));
            t.setTextSize(13);
            t.setTextColor(ContextCompat.getColor(this, R.color.primary));
            t.setBackgroundResource(R.drawable.bg_chip);
            t.setPadding(dp(14), dp(9), dp(14), dp(9));
            t.setOnClickListener(v -> {
                int dur = endMin > startMin ? endMin - startMin : DateUtil.DEFAULT_DURATION;
                startMin = r[0];
                endMin = Math.min(r[1], startMin + dur);
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chips.addView(t, lp);
        }
    }

    // --------------------------------------------------------------- lưu

    private boolean requireText(EditText et, String msg) {
        if (et.getText().toString().trim().isEmpty()) {
            et.setError(msg);
            et.requestFocus();
            return false;
        }
        return true;
    }

    private void save() {
        if (!requireText(etSubject, "Vui lòng nhập tên môn học")) return;
        if (!requireText(etClass, "Vui lòng nhập lớp")) return;
        if (!requireText(etRoom, "Vui lòng nhập phòng học")) return;
        if (endMin <= startMin) {
            Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (busy) return;
        Schedule s = new Schedule();
        s.id = editId;
        s.status = status;
        s.subject = etSubject.getText().toString().trim();
        s.className = etClass.getText().toString().trim();
        s.room = etRoom.getText().toString().trim();
        s.note = etNote.getText().toString().trim();
        s.date = dateIso;
        s.startMin = startMin;
        s.endMin = endMin;

        List<Schedule> conf = conflicts();
        if (!conf.isEmpty()) {
            refresh();
            showConflictDialog(s, conf);
            return;
        }

        setBusy(true);
        ScheduleRepository.Callback done = r -> {
            setBusy(false);
            if (r.ok()) {
                Toast.makeText(this, "Đã lưu lịch học", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else if (r.code == ScheduleRepository.Result.CONFLICT) {
                // DB là nơi quyết định cuối cùng về trùng lịch
                refresh();
                showConflictDialog(s, r.conflicts);
            } else {
                Toast.makeText(this, r.message != null ? r.message : "Không thể lưu lịch học. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
            }
        };
        if (editId != null) db.update(s, done);
        else db.insert(s, done);
    }

    private void forceSave(Schedule s) {
        setBusy(true);
        ScheduleRepository.Callback done = r -> {
            setBusy(false);
            if (r.ok()) {
                Toast.makeText(this, "Đã lưu lịch học (đã ghi nhận trùng giờ)", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, r.message != null ? r.message : "Không thể lưu lịch học.", Toast.LENGTH_LONG).show();
            }
        };
        if (editId != null) db.update(s, true, done);
        else db.insert(s, true, done);
    }

    private void setBusy(boolean b) {
        busy = b;
        btnSave.setEnabled(!b);
        btnSave.setText(b ? "Đang lưu..." : (editing != null ? "Lưu thay đổi" : "Lưu lịch"));
    }

    private void showConflictDialog(Schedule toSave, List<Schedule> fromServer) {
        List<Schedule> conf = fromServer != null && !fromServer.isEmpty() ? fromServer : conflicts();
        List<int[]> free = freeSlotsGap();
        Calendar c = DateUtil.parse(dateIso);

        StringBuilder sb = new StringBuilder();
        sb.append("Lịch học này trùng khung giờ với:\n");
        for (Schedule x : conf) {
            sb.append("• ").append(x.subject).append(" (").append(x.timeRange()).append(", phòng ").append(x.room).append(")\n");
        }
        sb.append("\nBạn vẫn muốn lưu lịch này chứ? (Ứng dụng cho phép lưu lịch trùng và chọn môn ưu tiên hiển thị trên Thời khóa biểu).");

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle("Cảnh báo trùng lịch học")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Vẫn lưu lịch", (dialog, which) -> forceSave(toSave))
                .setNegativeButton("Chỉnh sửa lại", null);

        final int dur = endMin - startMin;
        final int st = firstFreeStartGap(dur, DateUtil.DAY_START);
        if (st >= 0) {
            b.setNeutralButton("Chọn giờ trống", (d, w) -> {
                startMin = st;
                endMin = st + dur;
                refresh();
            });
        }
        b.show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa lịch học?")
                .setMessage("Buổi học “" + editing.subject + "” sẽ bị xóa vĩnh viễn.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> db.delete(editId, r -> {
                    if (r.ok()) {
                        Toast.makeText(this, "Đã xóa lịch học", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show();
                    }
                }))
                .show();
    }
}