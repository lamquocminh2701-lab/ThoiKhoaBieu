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
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.notify.NotificationHelper;
import com.example.thoikhoabieu.notify.ReminderScheduler;
import com.example.thoikhoabieu.util.DateUtil;
import com.example.thoikhoabieu.util.Prefs;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;

public class SettingsFragment extends Fragment {
    private boolean enabled, sound, vibrate, quiet;
    private int lead, qStart, qEnd;

    private MaterialSwitch swReminder, swSound, swVibrate, swQuiet;
    private TextView tvPerm, tvQuietRange, tvPreview, chip5, chip15, chip30;
    private View body;

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

        swReminder = v.findViewById(R.id.sw_reminder);
        swSound = v.findViewById(R.id.sw_sound);
        swVibrate = v.findViewById(R.id.sw_vibrate);
        swQuiet = v.findViewById(R.id.sw_quiet);
        tvPerm = v.findViewById(R.id.tv_perm_status);
        tvQuietRange = v.findViewById(R.id.tv_quiet_range);
        tvPreview = v.findViewById(R.id.tv_preview);
        chip5 = v.findViewById(R.id.tv_lead_5);
        chip15 = v.findViewById(R.id.tv_lead_15);
        chip30 = v.findViewById(R.id.tv_lead_30);
        body = v.findViewById(R.id.settings_body);

        swReminder.setChecked(enabled);
        swSound.setChecked(sound);
        swVibrate.setChecked(vibrate);
        swQuiet.setChecked(quiet);

        swReminder.setOnCheckedChangeListener((bv, on) -> {
            enabled = on;
            if (on) requestPermissionIfNeeded();
            updateUi();
        });
        swSound.setOnCheckedChangeListener((bv, on) -> { sound = on; updateUi(); });
        swVibrate.setOnCheckedChangeListener((bv, on) -> { vibrate = on; updateUi(); });
        swQuiet.setOnCheckedChangeListener((bv, on) -> { quiet = on; updateUi(); });

        chip5.setOnClickListener(x -> { lead = 5; updateUi(); });
        chip15.setOnClickListener(x -> { lead = 15; updateUi(); });
        chip30.setOnClickListener(x -> { lead = 30; updateUi(); });

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
        tvQuietRange.setText(DateUtil.hm(qStart) + " – " + DateUtil.hm(qEnd));
        body.setAlpha(enabled ? 1f : 0.45f);
        setEnabledDeep(body, enabled);
        tvQuietRange.setEnabled(enabled && quiet);
        updatePermStatus();
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
        ReminderScheduler.scheduleNext(ctx);
        Toast.makeText(ctx, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
    }
}
