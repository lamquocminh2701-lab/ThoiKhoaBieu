package com.example.thoikhoabieu.ui;

import android.content.Context;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.model.Schedule;

/** Hiển thị trạng thái môn học dưới dạng huy hiệu màu. */
public final class StatusStyle {
    private StatusStyle() {
    }

    public static void apply(TextView tv, String status) {
        Context c = tv.getContext();
        tv.setText(Schedule.statusLabel(status));
        if (Schedule.HOAN_THANH.equals(status)) {
            tv.setBackgroundResource(R.drawable.bg_status_done);
            tv.setTextColor(ContextCompat.getColor(c, R.color.success));
        } else if (Schedule.TAM_NGHI.equals(status)) {
            tv.setBackgroundResource(R.drawable.bg_status_pause);
            tv.setTextColor(ContextCompat.getColor(c, R.color.white));
        } else {
            tv.setBackgroundResource(R.drawable.bg_status_active);
            tv.setTextColor(ContextCompat.getColor(c, R.color.warn_text));
        }
    }
}
