package com.example.thoikhoabieu.conflict;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.thoikhoabieu.AddEditActivity;
import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.ui.StatusStyle;
import com.example.thoikhoabieu.util.DateUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Màn hình giải quyết và quản lý các buổi học bị trùng giờ:
 * - Hiển thị danh sách các buổi học cùng rơi vào một khung giờ.
 * - Cho phép chọn 1 buổi học làm ƯU TIÊN hiển thị và nhắc nhở thông báo.
 * - Cho phép chỉnh sửa từng buổi học để đổi giờ nếu muốn.
 */
public class ConflictResolutionActivity extends AppCompatActivity {
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_SCHEDULE_IDS = "extra_schedule_ids";

    public static Intent createIntent(Context context, String dateIso, ArrayList<String> scheduleIds) {
        Intent intent = new Intent(context, ConflictResolutionActivity.class);
        intent.putExtra(EXTRA_DATE, dateIso);
        intent.putStringArrayListExtra(EXTRA_SCHEDULE_IDS, scheduleIds);
        return intent;
    }

    private String dateIso;
    private ArrayList<String> scheduleIds;
    private ScheduleRepository repo;
    private LinearLayout containerItems;
    private TextView tvSubtitle;
    private TextView tvWarnTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conflict_resolution);
        repo = ScheduleRepository.get(this);

        dateIso = getIntent().getStringExtra(EXTRA_DATE);
        scheduleIds = getIntent().getStringArrayListExtra(EXTRA_SCHEDULE_IDS);

        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvWarnTitle = findViewById(R.id.tv_warn_title);
        containerItems = findViewById(R.id.container_conflict_items);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
        findViewById(R.id.btn_done).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndDisplayConflicts();
    }

    private void loadAndDisplayConflicts() {
        if (dateIso == null) {
            finish();
            return;
        }

        List<Schedule> allDaySchedules = repo.getByDate(dateIso);
        List<Schedule> groupSchedules = new ArrayList<>();

        if (scheduleIds != null && !scheduleIds.isEmpty()) {
            for (String id : scheduleIds) {
                Schedule s = repo.getById(id);
                if (s != null) groupSchedules.add(s);
            }
        }

        // Nếu danh sách id không đủ hoặc null, tìm conflict group tương ứng
        if (groupSchedules.isEmpty()) {
            List<ConflictGroup> groups = ConflictDetector.groupConflicts(allDaySchedules);
            for (ConflictGroup g : groups) {
                if (g.isConflict()) {
                    groupSchedules.addAll(g.schedules);
                    break;
                }
            }
        }

        if (groupSchedules.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy lịch học trùng giờ.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Cập nhật lại danh sách scheduleIds
        scheduleIds = new ArrayList<>();
        int minStart = 1440, maxEnd = 0;
        boolean hasPriority = false;
        for (Schedule s : groupSchedules) {
            scheduleIds.add(s.id);
            minStart = Math.min(minStart, s.startMin);
            maxEnd = Math.max(maxEnd, s.endMin);
            if (s.isPriority) hasPriority = true;
        }

        // Nếu chưa có lịch nào được đánh dấu ưu tiên, tự động gán cho buổi học đầu tiên
        if (!hasPriority && !groupSchedules.isEmpty()) {
            groupSchedules.get(0).isPriority = true;
            repo.setPriority(groupSchedules.get(0).id, scheduleIds, null);
        }

        Calendar cal = DateUtil.parse(dateIso);
        tvSubtitle.setText(DateUtil.dowFull(cal) + ", " + DateUtil.dm(cal) + " · " + DateUtil.hm(minStart) + "–" + DateUtil.hm(maxEnd));
        tvWarnTitle.setText("⚠️ Phát hiện " + groupSchedules.size() + " buổi học trùng khung giờ");

        containerItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Schedule s : groupSchedules) {
            View card = inflater.inflate(R.layout.item_conflict_schedule, containerItems, false);
            bindScheduleCard(card, s);
            containerItems.addView(card);
        }
    }

    private void bindScheduleCard(View card, Schedule s) {
        TextView tvSubject = card.findViewById(R.id.tv_subject);
        TextView tvBadgePriority = card.findViewById(R.id.tv_badge_priority);
        TextView tvTimeRange = card.findViewById(R.id.tv_time_range);
        TextView tvStatus = card.findViewById(R.id.tv_status);
        TextView tvRoomClass = card.findViewById(R.id.tv_room_class);
        TextView tvNote = card.findViewById(R.id.tv_note);
        MaterialButton btnEdit = card.findViewById(R.id.btn_edit_schedule);
        MaterialButton btnSelect = card.findViewById(R.id.btn_select_priority);

        tvSubject.setText(s.subject);
        tvTimeRange.setText(s.timeRange() + " (" + s.periods() + " tiết)");
        StatusStyle.apply(tvStatus, s.status);
        tvRoomClass.setText("Phòng " + (s.room.isEmpty() ? "—" : s.room) + " · Lớp " + (s.className.isEmpty() ? "—" : s.className));

        if (s.hasNote()) {
            tvNote.setVisibility(View.VISIBLE);
            tvNote.setText("Ghi chú: " + s.note.trim());
        } else {
            tvNote.setVisibility(View.GONE);
        }

        if (s.isPriority) {
            card.setBackgroundResource(R.drawable.bg_card_selected);
            tvBadgePriority.setVisibility(View.VISIBLE);
            btnSelect.setText("✓ Đang ưu tiên");
            btnSelect.setEnabled(false);
            btnSelect.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_light));
            btnSelect.setTextColor(ContextCompat.getColor(this, R.color.primary));
        } else {
            card.setBackgroundResource(R.drawable.bg_card);
            tvBadgePriority.setVisibility(View.GONE);
            btnSelect.setText("Chọn ưu tiên");
            btnSelect.setEnabled(true);
            btnSelect.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
            btnSelect.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnSelect.setOnClickListener(v -> selectPriority(s));
        }

        btnEdit.setOnClickListener(v -> startActivity(AddEditActivity.intentEdit(this, s.id)));
    }

    private void selectPriority(Schedule selected) {
        repo.setPriority(selected.id, scheduleIds, r -> {
            if (r.ok()) {
                Toast.makeText(this, "Đã chọn \"" + selected.subject + "\" làm lịch ưu tiên", Toast.LENGTH_SHORT).show();
                loadAndDisplayConflicts();
            } else {
                Toast.makeText(this, "Lỗi khi cập nhật ưu tiên: " + r.message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
