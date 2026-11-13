package com.example.thoikhoabieu.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.thoikhoabieu.MainActivity;
import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.notify.NotificationHelper;
import com.example.thoikhoabieu.notify.ReminderScheduler;
import com.example.thoikhoabieu.trash.TrashActivity;
import com.example.thoikhoabieu.trash.TrashManager;
import com.example.thoikhoabieu.util.DateUtil;
import com.example.thoikhoabieu.util.Prefs;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.List;

public class SettingsFragment extends Fragment {
    private boolean enabled, sound, vibrate, quiet, cleanupEnabled;
    private int lead, qStart, qEnd, retentionDays;

    private MaterialSwitch swReminder, swSound, swVibrate, swQuiet, swCleanup;
    private TextView tvPerm, tvQuietRange, tvPreview, chip5, chip15, chip30;
    private TextView tvRet15, tvRet17, tvRet20, tvTrashBadge;
    private View body, cleanupBody, layoutRowTrash, btnTriggerCleanupNow;

    private final ActivityResultLauncher<String> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (!granted) openNotificationSettings();
                updatePermStatus();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_settings, container, false);
        Context ctx = requireContext();
        enabled = Prefs.reminderEnabled(ctx);
        lead = Prefs.leadMinutes(ctx);
        sound = Prefs.sound(ctx);
        vibrate = Prefs.vibrate(ctx);
        quiet = Prefs.quietEnabled(ctx);
        qStart = Prefs.quietStart(ctx);
        qEnd = Prefs.quietEnd(ctx);
        cleanupEnabled = Prefs.cleanupPromptEnabled(ctx);
        retentionDays = Prefs.retentionDays(ctx);

        swReminder = v.findViewById(R.id.sw_reminder);
        swSound = v.findViewById(R.id.sw_sound);
        swVibrate = v.findViewById(R.id.sw_vibrate);
        swQuiet = v.findViewById(R.id.sw_quiet);
        swCleanup = v.findViewById(R.id.sw_cleanup);
        tvPerm = v.findViewById(R.id.tv_perm_status);
        tvQuietRange = v.findViewById(R.id.tv_quiet_range);
        tvPreview = v.findViewById(R.id.tv_preview);
        chip5 = v.findViewById(R.id.tv_lead_5);
        chip15 = v.findViewById(R.id.tv_lead_15);
        chip30 = v.findViewById(R.id.tv_lead_30);
        body = v.findViewById(R.id.settings_body);

        cleanupBody = v.findViewById(R.id.cleanup_settings_body);
        tvRet15 = v.findViewById(R.id.tv_retention_15);
        tvRet17 = v.findViewById(R.id.tv_retention_17);
        tvRet20 = v.findViewById(R.id.tv_retention_20);
        layoutRowTrash = v.findViewById(R.id.layout_row_trash);
        tvTrashBadge = v.findViewById(R.id.tv_trash_badge);
        btnTriggerCleanupNow = v.findViewById(R.id.btn_trigger_cleanup_now);

        swReminder.setChecked(enabled);
        swSound.setChecked(sound);
        swVibrate.setChecked(vibrate);
        swQuiet.setChecked(quiet);
        swCleanup.setChecked(cleanupEnabled);

        swReminder.setOnCheckedChangeListener((bv, on) -> {
            enabled = on;
            if (on) requestPermissionIfNeeded();
            updateUi();
        });
        swSound.setOnCheckedChangeListener((bv, on) -> { sound = on; updateUi(); });
        swVibrate.setOnCheckedChangeListener((bv, on) -> { vibrate = on; updateUi(); });
        swQuiet.setOnCheckedChangeListener((bv, on) -> { quiet = on; updateUi(); });
        swCleanup.setOnCheckedChangeListener((bv, on) -> { cleanupEnabled = on; updateUi(); });

        chip5.setOnClickListener(x -> { lead = 5; updateUi(); });
        chip15.setOnClickListener(x -> { lead = 15; updateUi(); });
        chip30.setOnClickListener(x -> { lead = 30; updateUi(); });

        tvRet15.setOnClickListener(x -> { retentionDays = 15; updateUi(); });
        tvRet17.setOnClickListener(x -> { retentionDays = 17; updateUi(); });
        tvRet20.setOnClickListener(x -> { retentionDays = 20; updateUi(); });

        layoutRowTrash.setOnClickListener(x -> startActivity(new Intent(requireContext(), TrashActivity.class)));
        btnTriggerCleanupNow.setOnClickListener(x -> manualTriggerCleanup());

        tvQuietRange.setOnClickListener(x -> pickQuietRange());
        tvPerm.setOnClickListener(x -> {
            if (!NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) requestPermissionIfNeeded();
        });
        v.findViewById(R.id.btn_back_settings).setOnClickListener(x -> ((MainActivity) requireActivity()).openTab(R.id.nav_home));
        v.findViewById(R.id.btn_save_settings).setOnClickListener(x -> save());
        updateUi();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermStatus();
        updateTrashBadge();
    }

    private void updateTrashBadge() {
        if (!isAdded()) return;
        int count = ScheduleRepository.get(requireContext()).getTrashCount();
        tvTrashBadge.setText(count + " buổi");
    }

    private void manualTriggerCleanup() {
        Context ctx = requireContext();
        List<Schedule> eligible = ScheduleRepository.get(ctx).getEligibleOldSchedules(retentionDays);
        if (eligible.isEmpty()) {
            Toast.makeText(ctx, "Không có buổi học nào đã kết thúc quá " + retentionDays + " ngày.", Toast.LENGTH_SHORT).show();
        } else {
            TrashManager.showCleanupDialog(requireActivity(), ScheduleRepository.get(ctx), eligible, retentionDays);
        }
    }

    private void requestPermissionIfNeeded() {
        Context ctx = requireContext();
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        else openNotificationSettings();
    }

    private void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(i);
    }

    private void updatePermStatus() {
        if (!isAdded()) return;
        boolean ok = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
        tvPerm.setText(ok ? "Đã cho phép thông báo" : "Chưa cấp quyền thông báo · chạm để cấp");
        tvPerm.setTextColor(ContextCompat.getColor(requireContext(), ok ? R.color.success : R.color.danger));
    }

    private void pickQuietRange() {
        new TimePickerDialog(requireContext(), (view, h, m) -> {
            int s = h * 60 + m;
            new TimePickerDialog(requireContext(), (view2, h2, m2) -> {
                qStart = s;
                qEnd = h2 * 60 + m2;
                updateUi();
            }, qEnd / 60, qEnd % 60, true).show();
            Toast.makeText(requireContext(), "Chọn giờ kết thúc yên lặng", Toast.LENGTH_SHORT).show();
        }, qStart / 60, qStart % 60, true).show();
        Toast.makeText(requireContext(), "Chọn giờ bắt đầu yên lặng", Toast.LENGTH_SHORT).show();
    }

    private void styleLead(TextView t, int value) {
        boolean sel = lead == value;
        t.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        t.setTextColor(ContextCompat.getColor(requireContext(), sel ? R.color.primary : R.color.text_primary));
        t.setText((sel ? "✓ " : "") + value + " phút");
        t.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void styleRetention(TextView t, int value) {
        boolean sel = retentionDays == value;
        t.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        t.setTextColor(ContextCompat.getColor(requireContext(), sel ? R.color.primary : R.color.text_primary));
        t.setText((sel ? "✓ " : "") + value + " ngày");
        t.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), on);
        }
    }

    private void updateUi() {
        styleLead(chip5, 5);
        styleLead(chip15, 15);
        styleLead(chip30, 30);
        styleRetention(tvRet15, 15);
        styleRetention(tvRet17, 17);
        styleRetention(tvRet20, 20);
        tvQuietRange.setText(DateUtil.hm(qStart) + " – " + DateUtil.hm(qEnd));
        body.setAlpha(enabled ? 1f : 0.45f);
        setEnabledDeep(body, enabled);
        cleanupBody.setAlpha(cleanupEnabled ? 1f : 0.45f);
        setEnabledDeep(cleanupBody, cleanupEnabled);
        tvQuietRange.setEnabled(enabled && quiet);
        updatePermStatus();
        updateTrashBadge();
        updatePreview();
    }

    private void updatePreview() {
        Context ctx = requireContext();
        if (!enabled) {
            tvPreview.setText("Nhắc lịch học đang tắt.");
            return;
        }
        Schedule next = ReminderScheduler.nextUpcoming(ctx);
        if (next == null) {
            tvPreview.setText("Chưa có buổi học sắp tới cần nhắc.");
            return;
        }
        long trigger = DateUtil.toMillis(next.date, next.startMin) - lead * 60_000L;
        Calendar tc = Calendar.getInstance();
        tc.setTimeInMillis(trigger);
        String trigIso = DateUtil.iso(tc);
        String todayIso = DateUtil.iso(Calendar.getInstance());
        String tomorrowIso = DateUtil.iso(DateUtil.addDays(DateUtil.today(), 1));
        String day = trigIso.equals(todayIso) ? "Hôm nay" : trigIso.equals(tomorrowIso) ? "Ngày mai" : DateUtil.dm(tc);
        int trigMin = tc.get(Calendar.HOUR_OF_DAY) * 60 + tc.get(Calendar.MINUTE);

        StringBuilder sb = new StringBuilder();
        sb.append(day).append(", nhắc ").append(next.subject).append(" lúc ").append(DateUtil.hm(trigMin))
                .append(", trước giờ học ").append(DateUtil.hm(next.startMin)).append(".");
        if (NotificationHelper.inQuiet(quiet, qStart, qEnd, trigMin)) {
            sb.append(" Lúc đó đang trong giờ yên lặng nên sẽ không phát âm thanh hoặc rung.");
        }
        tvPreview.setText(sb);
    }

    private void save() {
        Context ctx = requireContext();
        Prefs.setReminderEnabled(ctx, enabled);
        Prefs.setLeadMinutes(ctx, lead);
        Prefs.setSound(ctx, sound);
        Prefs.setVibrate(ctx, vibrate);
        Prefs.setQuietEnabled(ctx, quiet);
        Prefs.setQuietRange(ctx, qStart, qEnd);
        Prefs.setCleanupPromptEnabled(ctx, cleanupEnabled);
        Prefs.setRetentionDays(ctx, retentionDays);
        ReminderScheduler.scheduleNext(ctx);
        Toast.makeText(ctx, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
    }
}
