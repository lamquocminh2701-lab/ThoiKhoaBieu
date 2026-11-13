package com.example.thoikhoabieu.trash;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.ui.StatusStyle;
import com.example.thoikhoabieu.util.DateUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Quản lý Thùng rác:
 * - Xem danh sách các buổi học đã đưa vào thùng rác.
 * - Hiển thị bộ đếm ngược thời gian lưu trữ còn lại trước khi xóa vĩnh viễn (tối đa 6 ngày).
 * - Khôi phục từng buổi học hoặc khôi phục tất cả.
 * - Xuất file nhật ký TXT lưu trữ bằng Storage Access Framework (SAF).
 */
public class TrashActivity extends AppCompatActivity {

    private ScheduleRepository repo;
    private TextView tvCountSubtitle;
    private View layoutActionsHeader;
    private LinearLayout containerItems;
    private View layoutEmpty;
    private MaterialButton btnExportTxt;
    private TextView tvRestoreAll;

    private List<Schedule> currentTrashList;

    private final ActivityResultLauncher<String> exportTxtLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), this::onExportTxtResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);
        repo = ScheduleRepository.get(this);

        tvCountSubtitle = findViewById(R.id.tv_trash_count_subtitle);
        layoutActionsHeader = findViewById(R.id.layout_actions_header);
        containerItems = findViewById(R.id.container_trash_items);
        layoutEmpty = findViewById(R.id.layout_empty_trash);
        btnExportTxt = findViewById(R.id.btn_export_txt);
        tvRestoreAll = findViewById(R.id.tv_restore_all);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvRestoreAll.setOnClickListener(v -> confirmRestoreAll());
        btnExportTxt.setOnClickListener(v -> startExportTxt());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tự động dọn dẹp các bản ghi quá hạn 6 ngày
        repo.purgeExpiredTrash();
        loadTrash();
    }

    private void loadTrash() {
        currentTrashList = repo.getTrashSchedules();
        int count = currentTrashList.size();
        tvCountSubtitle.setText(count + " buổi học đang lưu trữ");

        if (count == 0) {
            layoutEmpty.setVisibility(View.VISIBLE);
            layoutActionsHeader.setVisibility(View.GONE);
            containerItems.removeAllViews();
            btnExportTxt.setEnabled(false);
            return;
        }

        layoutEmpty.setVisibility(View.GONE);
        layoutActionsHeader.setVisibility(View.VISIBLE);
        btnExportTxt.setEnabled(true);
        containerItems.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        for (Schedule s : currentTrashList) {
            View card = inflater.inflate(R.layout.item_trash_schedule, containerItems, false);

            TextView tvSubject = card.findViewById(R.id.tv_subject);
            TextView tvStatus = card.findViewById(R.id.tv_status);
            TextView tvDateTime = card.findViewById(R.id.tv_date_time);
            TextView tvRoomClass = card.findViewById(R.id.tv_room_class);
            TextView tvTrashedAt = card.findViewById(R.id.tv_trashed_at);
            TextView tvCountdown = card.findViewById(R.id.tv_countdown);
            MaterialButton btnRestore = card.findViewById(R.id.btn_restore);

            tvSubject.setText(s.subject);
            StatusStyle.apply(tvStatus, s.status);

            Calendar cal = DateUtil.parse(s.date);
            tvDateTime.setText(s.date + " (" + DateUtil.dowShort(cal) + ") · " + s.timeRange() + " (" + s.periods() + " tiết)");
            tvRoomClass.setText("Phòng " + (s.room.isEmpty() ? "—" : s.room) + " · Lớp " + (s.className.isEmpty() ? "—" : s.className));

            long trashedTime = s.trashedAt != null ? s.trashedAt : System.currentTimeMillis();
            tvTrashedAt.setText("Đưa vào thùng rác: " + sdf.format(new Date(trashedTime)));

            String countdownStr = s.timeLeftText();
            tvCountdown.setText("⏳ " + (countdownStr.isEmpty() ? "Đã lưu trong thùng rác" : countdownStr));

            btnRestore.setOnClickListener(v -> restoreSchedule(s));

            containerItems.addView(card);
        }
    }

    private void restoreSchedule(Schedule s) {
        repo.restoreFromTrash(s.id, r -> {
            if (r.ok()) {
                Toast.makeText(this, "Đã khôi phục buổi học: " + s.subject, Toast.LENGTH_SHORT).show();
                loadTrash();
            } else {
                Toast.makeText(this, "Lỗi khôi phục: " + r.message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmRestoreAll() {
        if (currentTrashList == null || currentTrashList.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Khôi phục tất cả?")
                .setMessage("Tất cả " + currentTrashList.size() + " buổi học trong thùng rác sẽ được đưa trở lại thời khóa biểu.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Khôi phục", (d, w) -> {
                    repo.restoreAllTrash(r -> {
                        if (r.ok()) {
                            Toast.makeText(this, "Đã khôi phục toàn bộ thùng rác.", Toast.LENGTH_SHORT).show();
                            loadTrash();
                        } else {
                            Toast.makeText(this, "Lỗi khi khôi phục: " + r.message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    private void startExportTxt() {
        if (currentTrashList == null || currentTrashList.isEmpty()) {
            Toast.makeText(this, "Thùng rác trống, không có dữ liệu để xuất.", Toast.LENGTH_SHORT).show();
            return;
        }
        String fileName = TrashLogExporter.generateDefaultFileName();
        exportTxtLauncher.launch(fileName);
    }

    private void onExportTxtResult(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                Toast.makeText(this, "Không thể mở tệp tin để ghi dữ liệu.", Toast.LENGTH_LONG).show();
                return;
            }
            TrashLogExporter.writeToStream(os, currentTrashList);
            Toast.makeText(this, "Đã xuất nhật ký TXT thành công!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi xuất tệp tin TXT: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
