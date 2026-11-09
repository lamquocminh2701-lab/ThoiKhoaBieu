package com.example.thoikhoabieu.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.thoikhoabieu.AddEditActivity;
import com.example.thoikhoabieu.MainActivity;
import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.DateUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Màn "Chi tiết lịch học": các buổi học trong ngày kèm ghi chú và khoảng trống. */
public class DetailFragment extends Fragment {
    private TextView tvDate, tvWeekRange, tvWeekPeriods, tvTodaySessions, tvTodayDetail, tvUpcomingDate, tvEmpty, tvSection;
    private LinearLayout list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_detail, container, false);
        tvDate = v.findViewById(R.id.tv_date);
        tvWeekRange = v.findViewById(R.id.tv_week_range);
        tvWeekPeriods = v.findViewById(R.id.tv_week_periods);
        tvTodaySessions = v.findViewById(R.id.tv_today_sessions);
        tvTodayDetail = v.findViewById(R.id.tv_today_detail);
        tvUpcomingDate = v.findViewById(R.id.tv_upcoming_date);
        tvSection = v.findViewById(R.id.tv_section_title);
        tvEmpty = v.findViewById(R.id.tv_empty);
        list = v.findViewById(R.id.list_upcoming);

        v.findViewById(R.id.btn_bell).setOnClickListener(x -> ((MainActivity) requireActivity()).openTab(R.id.nav_settings));
        v.findViewById(R.id.tv_back_home).setOnClickListener(x -> ((MainActivity) requireActivity()).openTab(R.id.nav_home));
        v.findViewById(R.id.btn_add).setOnClickListener(x ->
                startActivity(AddEditActivity.intentAdd(requireContext(), DateUtil.iso(Calendar.getInstance()), -1, -1)));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Context ctx = requireContext();
        ScheduleRepository repo = ScheduleRepository.get(ctx);
        Calendar now = Calendar.getInstance();
        String todayIso = DateUtil.iso(now);
        int nowMin = DateUtil.nowMin();

        tvDate.setText(DateUtil.dowFull(now) + ", " + DateUtil.dmy(now));

        Calendar mon = DateUtil.mondayOf(now);
        Calendar sun = DateUtil.addDays(mon, 6);
        List<Schedule> week = repo.getRange(DateUtil.iso(mon), DateUtil.iso(sun));
        int weekPeriods = 0;
        for (Schedule s : week) weekPeriods += s.periods();
        tvWeekRange.setText("TUẦN " + DateUtil.dm(mon) + " – " + DateUtil.dm(sun));
        tvWeekPeriods.setText(String.valueOf(weekPeriods));

        List<Schedule> today = new ArrayList<>();
        Set<String> subjects = new HashSet<>();
        int todayPeriods = 0;
        for (Schedule s : week) {
            if (s.date.equals(todayIso)) {
                today.add(s);
                todayPeriods += s.periods();
                subjects.add(DateUtil.fold(s.subject));
            }
        }
        tvTodaySessions.setText(today.size() + " buổi học");
        tvTodayDetail.setText(todayPeriods + " tiết · " + subjects.size() + " môn");

        // Hiện lịch hôm nay nếu còn buổi chưa kết thúc, ngược lại hiện ngày có lịch kế tiếp.
        boolean remaining = false;
        for (Schedule s : today) if (s.endMin > nowMin) remaining = true;
        String showIso = remaining ? todayIso : repo.getNextDateAfter(todayIso);

        list.removeAllViews();
        if (showIso == null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvUpcomingDate.setVisibility(View.GONE);
            tvSection.setText("Lịch học sắp tới");
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        boolean isToday = showIso.equals(todayIso);
        Calendar showCal = DateUtil.parse(showIso);
        if (isToday) {
            tvSection.setText("Lịch học hôm nay");
            tvUpcomingDate.setVisibility(View.GONE);
        } else {
            tvSection.setText("Lịch học sắp tới");
            tvUpcomingDate.setVisibility(View.VISIBLE);
            if (showIso.equals(DateUtil.iso(DateUtil.addDays(DateUtil.today(), 1)))) tvUpcomingDate.setText("Ngày mai · " + DateUtil.dm(showCal));
            else tvUpcomingDate.setText(DateUtil.dowFull(showCal) + ", " + DateUtil.dmy(showCal));
        }

        List<Schedule> items = isToday ? today : repo.getByDate(showIso);
        LayoutInflater inf = LayoutInflater.from(ctx);
        boolean nextMarked = false;
        for (int i = 0; i < items.size(); i++) {
            Schedule s = items.get(i);
            boolean past = isToday && s.endMin <= nowMin;
            boolean ongoing = isToday && s.startMin <= nowMin && nowMin < s.endMin;
            boolean upcoming = false;
            if (!past && !nextMarked) {
                upcoming = true;
                nextMarked = true;
            }
            addClassCard(inf, ctx, s, past, ongoing, upcoming);
            if (i + 1 < items.size()) {
                Schedule n = items.get(i + 1);
                if (n.startMin - s.endMin >= DateUtil.MIN_GAP) addGapCard(inf, ctx, showIso, s.endMin, n.startMin);
            }
        }
    }

    private void addClassCard(LayoutInflater inf, Context ctx, Schedule s, boolean past, boolean ongoing, boolean upcoming) {
        View v = inf.inflate(R.layout.item_class_card, list, false);
        TextView tvTime = v.findViewById(R.id.tv_time);
        TextView tvBadge = v.findViewById(R.id.tv_badge);
        TextView tvTitle = v.findViewById(R.id.tv_title);
        TextView tvInfo = v.findViewById(R.id.tv_info);
        View stripe = v.findViewById(R.id.stripe);

        tvTime.setText(DateUtil.hm(s.startMin) + " – " + DateUtil.hm(s.endMin));
        tvTitle.setText(s.subject);
        tvInfo.setText("Phòng " + s.room + "  ·  " + s.className);
        StatusStyle.apply((TextView) v.findViewById(R.id.tv_status), s.status);

        if (s.hasNote()) {
            v.findViewById(R.id.note_block).setVisibility(View.VISIBLE);
            ((TextView) v.findViewById(R.id.tv_note)).setText(s.note.trim());
        }

        if (ongoing) {
            tvBadge.setText("ĐANG HỌC");
            tvBadge.setVisibility(View.VISIBLE);
        } else if (upcoming) {
            tvBadge.setText("SẮP TỚI");
            tvBadge.setVisibility(View.VISIBLE);
        } else {
            tvBadge.setVisibility(View.GONE);
        }
        boolean paused = Schedule.TAM_NGHI.equals(s.status);
        if (paused) {
            v.setBackgroundResource(R.drawable.bg_card_pause);
        } else {
            v.setBackgroundResource(upcoming || ongoing ? R.drawable.bg_card_selected : R.drawable.bg_card);
        }
        int accent = ContextCompat.getColor(ctx, paused ? R.color.danger : (past ? R.color.text_hint : R.color.primary));
        stripe.setBackgroundColor(accent);
        tvTime.setTextColor(accent);
        v.setAlpha(past ? 0.65f : 1f);
        v.setOnClickListener(x -> startActivity(AddEditActivity.intentEdit(ctx, s.id)));
        list.addView(v);
    }

    private void addGapCard(LayoutInflater inf, Context ctx, String dateIso, int from, int to) {
        View v = inf.inflate(R.layout.item_gap_card, list, false);
        ((TextView) v.findViewById(R.id.tv_gap_time)).setText(DateUtil.hm(from) + " – " + DateUtil.hm(to));
        v.setOnClickListener(x -> startActivity(AddEditActivity.intentAdd(ctx, dateIso, from, to)));
        list.addView(v);
    }
}
