package com.example.thoikhoabieu.notify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.thoikhoabieu.MainActivity;
import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.DateUtil;
import com.example.thoikhoabieu.util.Prefs;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String id = intent.getStringExtra(ReminderScheduler.EXTRA_ID);
        Schedule s = id != null ? ScheduleRepository.get(ctx).getById(id) : null;
        if (s != null && Prefs.reminderEnabled(ctx)) {
            long start = DateUtil.toMillis(s.date, s.startMin);
            if (start > System.currentTimeMillis()) show(ctx, s, start);
        }
        ReminderScheduler.scheduleNext(ctx);
    }

    @SuppressLint("MissingPermission")
    private void show(Context ctx, Schedule s, long startMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationHelper.createChannels(ctx);

        boolean quiet = NotificationHelper.inQuiet(Prefs.quietEnabled(ctx), Prefs.quietStart(ctx), Prefs.quietEnd(ctx), DateUtil.nowMin());
        boolean sound = !quiet && Prefs.sound(ctx);
        boolean vibrate = !quiet && Prefs.vibrate(ctx);

        long left = Math.max(1, (startMillis - System.currentTimeMillis() + 59_999) / 60_000);
        String text = "Còn " + left + " phút nữa vào học · " + DateUtil.hm(s.startMin) + " · Phòng " + s.room;
        String big = text + "\nLớp " + s.className + (s.note != null && !s.note.trim().isEmpty() ? "\nGhi chú: " + s.note.trim() : "");

        Intent open = new Intent(ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, s.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, NotificationHelper.channelId(sound, vibrate))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(s.subject)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority((sound || vibrate) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int defaults = 0;
            if (sound) defaults |= NotificationCompat.DEFAULT_SOUND;
            if (vibrate) defaults |= NotificationCompat.DEFAULT_VIBRATE;
            b.setDefaults(defaults);
        }
        NotificationManagerCompat.from(ctx).notify(s.id.hashCode(), b.build());
    }
}
