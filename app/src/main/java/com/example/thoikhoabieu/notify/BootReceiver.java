package com.example.thoikhoabieu.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Đặt lại báo thức sau khi khởi động lại máy, cập nhật app hoặc đổi giờ/múi giờ. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ReminderScheduler.scheduleNext(ctx);
    }
}
