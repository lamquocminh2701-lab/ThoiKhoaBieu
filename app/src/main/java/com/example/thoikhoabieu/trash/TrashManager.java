package com.example.thoikhoabieu.trash;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.Prefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Trình quản lý kiểm tra và nhắc dọn dẹp các buổi học cũ:
 * - Kích hoạt khi mở ứng dụng (onResume).
 * - Kiểm tra cấu hình Cài đặt (Bật/Tắt, thời hạn 15–20 ngày).
 * - Quản lý thời gian tạm hoãn (Snooze 7 ngày).
 * - Hiển thị hộp thoại nhắc nhở thân thiện, an toàn, không làm phiền người dùng.
 */
public final class TrashManager {

    private TrashManager() {
    }

    public static void checkAndPromptCleanup(FragmentActivity activity) {
        if (activity == null || activity.isFinishing()) return;

        Context ctx = activity.getApplicationContext();
        ScheduleRepository repo = ScheduleRepository.get(ctx);

        // 1. Tự động xóa vĩnh viễn các mục thùng rác đã quá hạn 6 ngày
        repo.purgeExpiredTrash();

        // 2. Kiểm tra tính năng dọn dẹp có được bật không
        if (!Prefs.cleanupPromptEnabled(ctx)) return;

        // 3. Kiểm tra thời gian tạm hoãn (Snooze 7 ngày)
        long lastDismissed = Prefs.lastCleanupDismissedTime(ctx);
        long now = System.currentTimeMillis();
        long snoozeDuration = 7 * 86_400_000L; // 7 ngày
        if (now - lastDismissed < snoozeDuration) {
            return;
        }

        // 4. Lấy danh sách các buổi học đủ điều kiện dọn dẹp
        int retentionDays = Prefs.retentionDays(ctx);
        List<Schedule> eligible = repo.getEligibleOldSchedules(retentionDays);
        if (eligible == null || eligible.isEmpty()) {
            return;
        }

        // 5. Hiển thị hộp thoại hỏi người dùng
        showCleanupDialog(activity, repo, eligible, retentionDays);
    }

    public static void showCleanupDialog(FragmentActivity activity, ScheduleRepository repo, List<Schedule> eligible, int retentionDays) {
        if (activity == null || activity.isFinishing()) return;

        int count = eligible.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Phát hiện ").append(count).append(" buổi học đã kết thúc hơn ")
                .append(retentionDays).append(" ngày trước.\n\n")
                .append("Bạn có muốn chuyển các buổi học này vào thùng rác để thời khóa biểu gọn gàng hơn không?\n\n")
                .append("• Lịch trong thùng rác có thể khôi phục trong vòng 6 ngày.\n")
                .append("• Bạn có thể xuất tệp TXT để lưu lại toàn bộ thông tin buổi học.");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Dọn dẹp lịch học cũ")
                .setMessage(sb.toString())
                .setPositiveButton("Chuyển vào thùng rác", (dialog, which) -> {
                    List<String> ids = new ArrayList<>();
                    for (Schedule s : eligible) ids.add(s.id);
                    repo.moveToTrash(ids, r -> {
                        if (r.ok()) {
                            Toast.makeText(activity, "Đã chuyển " + count + " buổi học vào thùng rác", Toast.LENGTH_SHORT).show();
                            // Hỏi người dùng có muốn mở thùng rác để xuất file TXT không
                            promptViewTrash(activity);
                        } else {
                            Toast.makeText(activity, "Lỗi: " + r.message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNeutralButton("Để sau", (dialog, which) -> {
                    Prefs.setLastCleanupDismissedTime(activity, System.currentTimeMillis());
                    Toast.makeText(activity, "Sẽ nhắc lại dọn dẹp sau 7 ngày.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private static void promptViewTrash(FragmentActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Đã chuyển vào thùng rác")
                .setMessage("Bạn có muốn mở Thùng rác để xem danh sách hoặc xuất file nhật ký TXT lưu trữ không?")
                .setPositiveButton("Xem thùng rác", (d, w) -> {
                    activity.startActivity(new Intent(activity, TrashActivity.class));
                })
                .setNegativeButton("Đóng", null)
                .show();
    }
}
