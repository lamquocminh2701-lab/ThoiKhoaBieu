package com.example.thoikhoabieu.notify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class NotificationHelper {
    private NotificationHelper() {
    }

    public static String channelId(boolean sound, boolean vibrate) {
        return "reminder_s" + (sound ? 1 : 0) + "_v" + (vibrate ? 1 : 0);
    }

    /**
     * Kênh thông báo không đổi được âm thanh/rung sau khi tạo (Android 8+),
     * nên tạo sẵn 4 kênh cho 4 tổ hợp và chọn kênh phù hợp khi gửi.
     */
    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        for (int s = 0; s < 2; s++) {
            for (int v = 0; v < 2; v++) {
                boolean sound = s == 1;
                boolean vib = v == 1;
                String name;
                if (sound && vib) name = "Nhắc lịch: âm thanh + rung";
                else if (sound) name = "Nhắc lịch: chỉ âm thanh";
                else if (vib) name = "Nhắc lịch: chỉ rung";
                else name = "Nhắc lịch: im lặng";
                NotificationChannel ch = new NotificationChannel(channelId(sound, vib), name,
                        (sound || vib) ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Nhắc trước giờ vào học");
                if (!sound) ch.setSound(null, null);
                ch.enableVibration(vib);
                nm.createNotificationChannel(ch);
            }
        }
    }

    /** Phút trong ngày có nằm trong giờ yên lặng (có thể vắt qua nửa đêm) không. */
    public static boolean inQuiet(boolean enabled, int start, int end, int minute) {
        if (!enabled || start == end) return false;
        if (start < end) return minute >= start && minute < end;
        return minute >= start || minute < end;
    }
}
