package com.example.thoikhoabieu.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.thoikhoabieu.AddEditActivity;
import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.model.Semester;
import com.example.thoikhoabieu.util.DateUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Trang chủ: thời khóa biểu theo tuần và theo tháng. */
public class HomeFragment extends Fragment {
    private static final String[] HEADS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    private boolean monthMode = false;
    private Calendar anchor = DateUtil.today();
    private String selectedIso = DateUtil.iso(Calendar.getInstance());

    private TextView tvModeWeek, tvModeMonth, tvTitle, tvSemester, tvRange, tvSummary, tvGoto, tvFoot, tvMonthDayTitle, tvEmptyDays;
    private ImageButton btnAdd;
    private LinearLayout summaryRow, weekContainer, monthContainer, monthHead, monthGrid, monthDayList;
    private ScheduleRepository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_home, container, false);
        repo = ScheduleRepository.get(requireContext());
        tvModeWeek = v.findViewById(R.id.tv_mode_week);
        tvModeMonth = v.findViewById(R.id.tv_mode_month);
        tvTitle = v.findViewById(R.id.tv_week_title);
        tvSemester = v.findViewById(R.id.tv_week_semester);
        tvRange = v.findViewById(R.id.tv_range);
        tvSummary = v.findViewById(R.id.tv_summary);
        tvGoto = v.findViewById(R.id.tv_goto_today);
        tvFoot = v.findViewById(R.id.tv_foot);
        tvEmptyDays = v.findViewById(R.id.tv_empty_days);
        tvMonthDayTitle = v.findViewById(R.id.tv_month_day_title);
        btnAdd = v.findViewById(R.id.btn_week_add);
        summaryRow = v.findViewById(R.id.summary_row);
        weekContainer = v.findViewById(R.id.week_container);
        monthContainer = v.findViewById(R.id.month_container);
        monthHead = v.findViewById(R.id.month_head);
        monthGrid = v.findViewById(R.id.month_grid);
        monthDayList = v.findViewById(R.id.month_day_list);

        tvModeWeek.setOnClickListener(x -> setMode(false));
        tvModeMonth.setOnClickListener(x -> setMode(true));
        v.findViewById(R.id.btn_prev).setOnClickListener(x -> shift(-1));
        v.findViewById(R.id.btn_next).setOnClickListener(x -> shift(1));
        tvGoto.setOnClickListener(x -> {
            anchor = DateUtil.today();
            selectedIso = DateUtil.iso(anchor);
            render();
        });
        btnAdd.setOnClickListener(x -> startActivity(
                AddEditActivity.intentAdd(requireContext(), monthMode ? selectedIso : defaultDayOfWeek(), -1, -1)));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    /** Hôm nay nếu nằm trong tuần đang xem, ngược lại là thứ Hai của tuần đó. */
    private String defaultDayOfWeek() {
        Calendar mon = DateUtil.mondayOf(anchor);
        String todayIso = DateUtil.iso(Calendar.getInstance());
        String monIso = DateUtil.iso(mon);
        String sunIso = DateUtil.iso(DateUtil.addDays(mon, 6));
        return (todayIso.compareTo(monIso) >= 0 && todayIso.compareTo(sunIso) <= 0) ? todayIso : monIso;
    }

    private void setMode(boolean month) {
        if (month == monthMode) return;
        if (month) selectedIso = defaultDayOfWeek();
        else anchor = DateUtil.parse(selectedIso);
        monthMode = month;
        render();
    }

    private void shift(int dir) {
        if (monthMode) {
            anchor.set(Calendar.DAY_OF_MONTH, 1);
            anchor.add(Calendar.MONTH, dir);
        } else {
            anchor.add(Calendar.DAY_OF_YEAR, 7 * dir);
        }
        render();
    }

    private void styleChip(TextView t, boolean selected) {
        t.setBackgroundResource(selected ? R.drawable.bg_pill_selected : R.drawable.bg_pill_outline);
        t.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.primary : R.color.text_secondary));
    }

    private void render() {
        if (!isAdded()) return;
        Context ctx = requireContext();
        styleChip(tvModeWeek, !monthMode);
        styleChip(tvModeMonth, monthMode);
        tvTitle.setText(monthMode ? "Thời khóa biểu tháng" : "Trang chủ");
        tvSemester.setText(Semester.forDate(anchor).label());
        tvGoto.setText("Hôm nay: " + DateUtil.dm(Calendar.getInstance()));
        weekContainer.setVisibility(monthMode ? View.GONE : View.VISIBLE);
        monthContainer.setVisibility(monthMode ? View.VISIBLE : View.GONE);

        // nút "+": viền xám ở chế độ tuần, nền xanh nhạt ở chế độ tháng (theo Figma)
        btnAdd.setBackgroundResource(monthMode ? R.drawable.bg_icon_tile : R.drawable.bg_btn_square);
        btnAdd.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, monthMode ? R.color.primary : R.color.text_secondary)));

        // tuần: "Hôm nay" nằm dòng riêng; tháng: cùng dòng, canh phải
        LinearLayout.LayoutParams lpSum = (LinearLayout.LayoutParams) tvSummary.getLayoutParams();
        if (monthMode) {
            summaryRow.setOrientation(LinearLayout.HORIZONTAL);
            lpSum.width = 0;
            lpSum.weight = 1f;
        } else {
            summaryRow.setOrientation(LinearLayout.VERTICAL);
            lpSum.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lpSum.weight = 0f;
        }
        tvSummary.setLayoutParams(lpSum);

        if (monthMode) renderMonth();
        else renderWeek();
    }

    // ------------------------------------------------------------- tuần

    private void renderWeek() {
        Context ctx = requireContext();
        Calendar mon = DateUtil.mondayOf(anchor);
        Calendar sun = DateUtil.addDays(mon, 6);
        List<Schedule> all = repo.getRange(DateUtil.iso(mon), DateUtil.iso(sun));
        Map<String, List<Schedule>> byDate = group(all);

        int periods = 0;
        for (Schedule s : all) periods += s.periods();
        tvRange.setText(DateUtil.dm(mon) + " – " + DateUtil.dmy(sun));
        tvSummary.setText(all.size() + " buổi học · " + periods + " tiết");

        weekContainer.removeAllViews();
        List<String> empty = new ArrayList<>();
        String todayIso = DateUtil.iso(Calendar.getInstance());
        for (int i = 0; i < 7; i++) {
            Calendar d = DateUtil.addDays(mon, i);
            List<Schedule> items = byDate.get(DateUtil.iso(d));
            if (items == null || items.isEmpty()) empty.add(DateUtil.dowShort(d) + ", " + DateUtil.dm(d));
            else bindPillDay(ctx, d, items, DateUtil.iso(d).equals(todayIso));
        }

        if (empty.isEmpty()) {
            tvEmptyDays.setVisibility(View.GONE);
        } else {
            tvEmptyDays.setVisibility(View.VISIBLE);
            tvEmptyDays.setText(empty.size() == 7
                    ? "Tuần này chưa có lịch học. Bấm + để thêm lịch."
                    : android.text.TextUtils.join(" & ", empty) + " · Trống");
        }
        tvFoot.setText(footText());
    }

    /** Thẻ ngày kiểu Figma: tiêu đề + huy hiệu, bên dưới là các hàng "pill". */
    private void bindPillDay(Context ctx, Calendar day, List<Schedule> items, boolean today) {
        LayoutInflater inf = LayoutInflater.from(ctx);
        View card = inf.inflate(R.layout.item_day_card, weekContainer, false);
        String iso = DateUtil.iso(day);
        TextView tvDow = card.findViewById(R.id.tv_dow);
        TextView tvNum = card.findViewById(R.id.tv_daynum);
        TextView tvBadge = card.findViewById(R.id.tv_badge);
        LinearLayout rows = card.findViewById(R.id.rows);

        tvDow.setText(DateUtil.dowShort(day));
        tvNum.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
        if (today) {
            card.setBackgroundResource(R.drawable.bg_card_today);
            tvNum.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
            tvBadge.setText("Hôm nay");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_today);
            tvBadge.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        } else {
            tvBadge.setText(items.size() + " buổi");
        }
        card.findViewById(R.id.day_header).setOnClickListener(x -> startActivity(AddEditActivity.intentAdd(ctx, iso, -1, -1)));

        for (int i = 0; i < items.size(); i++) {
            Schedule s = items.get(i);
            View row = inf.inflate(R.layout.item_row_pill, rows, false);
            ((TextView) row.findViewById(R.id.tv_row_time)).setText(s.timeRange());
            ((TextView) row.findViewById(R.id.tv_row_title)).setText(s.subject);
            ((TextView) row.findViewById(R.id.tv_row_room)).setText(s.room);
            TextView note = row.findViewById(R.id.tv_row_note);
            if (s.hasNote()) {
                note.setText(s.note.trim());
                note.setVisibility(View.VISIBLE);
                note.setBackgroundResource(today ? R.drawable.bg_note : R.drawable.bg_note_white);
            }
            row.setBackgroundResource(!today ? R.drawable.bg_row : (s.hasNote() ? R.drawable.bg_row_white : R.drawable.bg_row_today));
            if (Schedule.TAM_NGHI.equals(s.status)) {
                row.setBackgroundResource(R.drawable.bg_row_pause);
                int red = ContextCompat.getColor(ctx, R.color.danger);
                ((TextView) row.findViewById(R.id.tv_row_time)).setTextColor(red);
                ((TextView) row.findViewById(R.id.tv_row_title)).setTextColor(red);
            }
            row.setOnClickListener(x -> startActivity(AddEditActivity.intentEdit(ctx, s.id)));
            rows.addView(row);

            if (i + 1 < items.size()) {
                Schedule n = items.get(i + 1);
                if (n.startMin - s.endMin >= DateUtil.MIN_GAP) {
                    View gap = inf.inflate(R.layout.item_row_pill, rows, false);
                    ((TextView) gap.findViewById(R.id.tv_row_time)).setText(DateUtil.hm(s.endMin) + "–" + DateUtil.hm(n.startMin));
                    TextView title = gap.findViewById(R.id.tv_row_title);
                    title.setText("Trống");
                    title.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
                    ((TextView) gap.findViewById(R.id.tv_row_room)).setText("—");
                    gap.setBackgroundResource(today ? R.drawable.bg_row_today : R.drawable.bg_row);
                    int from = s.endMin, to = n.startMin;
                    gap.setOnClickListener(x -> startActivity(AddEditActivity.intentAdd(ctx, iso, from, to)));
                    rows.addView(gap);
                }
            }
        }
        weekContainer.addView(card);
    }

    // ------------------------------------------------------------ tháng

    private void renderMonth() {
        Context ctx = requireContext();
        LayoutInflater inf = LayoutInflater.from(ctx);
        tvEmptyDays.setVisibility(View.GONE);

        Calendar first = (Calendar) anchor.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        DateUtil.zero(first);
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar gridStart = DateUtil.mondayOf(first);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int rows = (offset + daysInMonth + 6) / 7;
        Calendar gridEnd = DateUtil.addDays(gridStart, rows * 7 - 1);

        List<Schedule> all = repo.getRange(DateUtil.iso(gridStart), DateUtil.iso(gridEnd));
        Map<String, List<Schedule>> byDate = group(all);

        String prefix = DateUtil.iso(first).substring(0, 7);
        String todayIso = DateUtil.iso(Calendar.getInstance());
        int sessions = 0, periods = 0;
        for (Schedule s : all) {
            if (s.date.startsWith(prefix)) {
                sessions++;
                periods += s.periods();
            }
        }
        tvRange.setText(String.format(Locale.US, "Tháng %d/%d", first.get(Calendar.MONTH) + 1, first.get(Calendar.YEAR)));
        tvSummary.setText(sessions + " buổi học · " + periods + " tiết");

        if (!selectedIso.startsWith(prefix)) selectedIso = todayIso.startsWith(prefix) ? todayIso : DateUtil.iso(first);

        monthHead.removeAllViews();
        for (String h : HEADS) {
            TextView t = new TextView(ctx);
            t.setText(h);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(12);
            t.setTypeface(t.getTypeface(), Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            t.setBackgroundResource(R.drawable.bg_cell);
            t.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            monthHead.addView(t, lp);
        }

        monthGrid.removeAllViews();
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 7; c++) {
                Calendar d = DateUtil.addDays(gridStart, r * 7 + c);
                String iso = DateUtil.iso(d);
                boolean inMonth = iso.startsWith(prefix);
                View cell = inf.inflate(R.layout.item_month_cell, row, false);
                TextView tv = cell.findViewById(R.id.tv_cell_day);
                tv.setText(String.format(Locale.US, "%02d", d.get(Calendar.DAY_OF_MONTH)));
                int color = !inMonth ? R.color.text_hint : (iso.equals(todayIso) ? R.color.primary : R.color.text_primary);
                tv.setTextColor(ContextCompat.getColor(ctx, color));
                cell.setBackgroundResource(iso.equals(selectedIso) ? R.drawable.bg_cell_selected : R.drawable.bg_cell);
                List<Schedule> items = byDate.get(iso);
                cell.findViewById(R.id.v_dot).setVisibility(inMonth && items != null && !items.isEmpty() ? View.VISIBLE : View.GONE);
                cell.setOnClickListener(x -> {
                    selectedIso = iso;
                    if (!inMonth) anchor = (Calendar) d.clone();
                    render();
                });
                row.addView(cell);
            }
            monthGrid.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        Calendar sel = DateUtil.parse(selectedIso);
        tvMonthDayTitle.setText("Lịch học ngày " + DateUtil.dm(sel));
        monthDayList.removeAllViews();
        List<Schedule> items = byDate.get(selectedIso);
        if (items == null || items.isEmpty()) {
            View v = inf.inflate(R.layout.item_gap_card, monthDayList, false);
            ((TextView) v.findViewById(R.id.tv_gap_time)).setText(DateUtil.dowFull(sel) + ", " + DateUtil.dmy(sel));
            v.setOnClickListener(x -> startActivity(AddEditActivity.intentAdd(ctx, selectedIso, -1, -1)));
            monthDayList.addView(v);
        } else {
            bindCompactDay(ctx, monthDayList, sel, items);
        }
        tvFoot.setText(footText());
    }

    // ---------------------------------------------------------- chung

    private Map<String, List<Schedule>> group(List<Schedule> all) {
        Map<String, List<Schedule>> m = new HashMap<>();
        for (Schedule s : all) {
            List<Schedule> l = m.get(s.date);
            if (l == null) {
                l = new ArrayList<>();
                m.put(s.date, l);
            }
            l.add(s);
        }
        return m;
    }

    private String footText() {
        String s = "Mỗi buổi 90 phút (2 tiết). Chạm lịch để chỉnh sửa.";
        return s;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Thẻ ngày gọn (dùng ở lịch tháng): số ngày bên trái, các buổi bên phải. */
    private void bindCompactDay(Context ctx, LinearLayout parent, Calendar day, List<Schedule> items) {
        LayoutInflater inf = LayoutInflater.from(ctx);
        View card = inf.inflate(R.layout.item_day_compact, parent, false);
        String iso = DateUtil.iso(day);
        TextView tvDow = card.findViewById(R.id.tv_dow);
        TextView tvNum = card.findViewById(R.id.tv_daynum);
        LinearLayout rows = card.findViewById(R.id.rows);

        tvDow.setText(DateUtil.dowShort(day));
        tvNum.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
        card.setBackgroundResource(R.drawable.bg_card_selected);
        tvNum.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        card.findViewById(R.id.col_day).setOnClickListener(x -> startActivity(AddEditActivity.intentAdd(ctx, iso, -1, -1)));

        for (int i = 0; i < items.size(); i++) {
            Schedule s = items.get(i);
            View row = inf.inflate(R.layout.item_week_row, rows, false);
            ((TextView) row.findViewById(R.id.tv_row_time)).setText(DateUtil.hm(s.startMin));
            ((TextView) row.findViewById(R.id.tv_row_title)).setText(s.subject);
            ((TextView) row.findViewById(R.id.tv_row_room)).setText(s.room);
            if (Schedule.TAM_NGHI.equals(s.status)) {
                int red = ContextCompat.getColor(ctx, R.color.danger);
                ((TextView) row.findViewById(R.id.tv_row_time)).setTextColor(red);
                ((TextView) row.findViewById(R.id.tv_row_title)).setTextColor(red);
                ((TextView) row.findViewById(R.id.tv_row_room)).setTextColor(red);
            }
            row.setOnClickListener(x -> startActivity(AddEditActivity.intentEdit(ctx, s.id)));
            rows.addView(row);

            if (i + 1 < items.size()) {
                Schedule n = items.get(i + 1);
                if (n.startMin - s.endMin >= DateUtil.MIN_GAP) {
                    View gap = inf.inflate(R.layout.item_week_row, rows, false);
                    TextView t = gap.findViewById(R.id.tv_row_time);
                    t.setText(DateUtil.hm(s.endMin) + "–" + DateUtil.hm(n.startMin));
                    t.setTextSize(12);
                    t.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
                    TextView title = gap.findViewById(R.id.tv_row_title);
                    title.setText("Trống");
                    title.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
                    ((TextView) gap.findViewById(R.id.tv_row_room)).setText("");
                    int from = s.endMin, to = n.startMin;
                    gap.setOnClickListener(x -> startActivity(AddEditActivity.intentAdd(ctx, iso, from, to)));
                    rows.addView(gap);
                }
            }
        }
        parent.addView(card);
    }
}
