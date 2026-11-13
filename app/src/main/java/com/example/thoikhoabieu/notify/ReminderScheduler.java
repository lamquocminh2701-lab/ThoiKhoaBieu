package com.example.thoikhoabieu.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.DateUtil;
import com.example.thoikhoabieu.util.Prefs;

import java.util.Calendar;
import java.util.List;

/**
 * Luôn chỉ đặt MỘT báo thức cho buổi học kế tiếp. Khi báo thức nổ (hoặc dữ liệu/cài đặt đổi)
 * thì tính và đặt lại cho buổi sau đó.
 */
public final class ReminderScheduler {
    public static final String EXTRA_ID = "schedule_id";
    private static final int REQUEST_CODE = 1001;

    private ReminderScheduler() {
    }

    private static PendingIntent pending(Context ctx, String id) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Buổi học đầu tiên có giờ bắt đầu sau hiện tại (trong 60 ngày tới), chỉ xét lịch ưu tiên nếu trùng giờ. */
    public static Schedule nextUpcoming(Context ctx) {
        long now = System.currentTimeMillis();
        Calendar today = DateUtil.today();
        List<Schedule> list = ScheduleRepository.get(ctx).getRange(DateUtil.iso(today), DateUtil.iso(DateUtil.addDays(today, 60)));
        List<Schedule> filtered = com.example.thoikhoabieu.conflict.ConflictDetector.filterPriorityOnly(list);
        for (Schedule s : filtered) {
            if (DateUtil.toMillis(s.date, s.startMin) > now) return s;
        }
        return null;
    }

    public static void scheduleNext(Context context) {
        Context ctx = context.getApplicationContext();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pending(ctx, null));
        if (!Prefs.reminderEnabled(ctx)) return;

        long now = System.currentTimeMillis();
        long lead = Prefs.leadMinutes(ctx) * 60_000L;
        Calendar today = DateUtil.today();
        List<Schedule> list = ScheduleRepository.get(ctx).getRange(DateUtil.iso(today), DateUtil.iso(DateUtil.addDays(today, 60)));
        List<Schedule> filtered = com.example.thoikhoabieu.conflict.ConflictDetector.filterPriorityOnly(list);
        for (Schedule s : filtered) {
            long trigger = DateUtil.toMillis(s.date, s.startMin) - lead;
            if (trigger > now) {
                setAlarm(ctx, am, trigger, pending(ctx, s.id));
                return;
            }
        }
    }

    private static void setAlarm(Context ctx, AlarmManager am, long trigger, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }
}
