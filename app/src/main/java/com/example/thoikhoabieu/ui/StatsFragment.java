package com.example.thoikhoabieu.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.model.Semester;
import com.example.thoikhoabieu.util.DateUtil;
import com.example.thoikhoabieu.util.ExportUtil;
import com.example.thoikhoabieu.util.Prefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsFragment extends Fragment {
    private static class Stat {
        String name;
        int periods;
        /** Trạng thái tổng hợp của môn: ưu tiên Đang học > Tạm nghỉ > Đã hoàn thành. */
        String status = Schedule.HOAN_THANH;
        /** Ghi chú gần nhất (theo ngày) của môn. */
        String note;
    }

    private static int rank(String status) {
        if (Schedule.DANG_HOC.equals(status)) return 3;
        if (Schedule.TAM_NGHI.equals(status)) return 2;
        return 1;
    }

    private Semester semester;
    private TextView tvSemester, tvTotal, tvSubjects, tvPercent, tvDone, tvDoneOf, tvRemaining, tvNoData, tvUpdated;
    private ProgressBar pb;
    private LinearLayout bars;
    private EditText etSearch;
    private List<Stat> stats = new ArrayList<>();
    private int maxPeriods = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_stats, container, false);
        tvSemester = v.findViewById(R.id.tv_semester);
        tvTotal = v.findViewById(R.id.tv_total_periods);
        tvSubjects = v.findViewById(R.id.tv_subject_count);
        tvPercent = v.findViewById(R.id.tv_percent);
        tvDone = v.findViewById(R.id.tv_done);
        tvDoneOf = v.findViewById(R.id.tv_done_of);
        tvRemaining = v.findViewById(R.id.tv_remaining);
        tvNoData = v.findViewById(R.id.tv_no_data);
        tvUpdated = v.findViewById(R.id.tv_updated);
        pb = v.findViewById(R.id.pb_progress);
        bars = v.findViewById(R.id.subject_bars);
        etSearch = v.findViewById(R.id.et_search);

        semester = Semester.fromKey(Prefs.semesterKey(requireContext()));
        if (semester == null) semester = Semester.forDate(Calendar.getInstance());

        v.findViewById(R.id.card_semester).setOnClickListener(x -> chooseSemester());
        v.findViewById(R.id.btn_export_csv).setOnClickListener(x -> export(false));
        v.findViewById(R.id.btn_export_pdf).setOnClickListener(x -> export(true));
        v.findViewById(R.id.btn_import_schedule).setOnClickListener(x -> {
            startActivity(new Intent(requireContext(), com.example.thoikhoabieu.importer.ImportScheduleActivity.class));
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { renderBars(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void chooseSemester() {
        List<Semester> options = Semester.options();
        String[] names = new String[options.size()];
        int checked = 0;
        for (int i = 0; i < options.size(); i++) {
            names[i] = options.get(i).label();
            if (options.get(i).key().equals(semester.key())) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chọn học kỳ")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    semester = options.get(which);
                    Prefs.setSemesterKey(requireContext(), semester.key());
                    d.dismiss();
                    refresh();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void refresh() {
        Context ctx = requireContext();
        List<Schedule> list = ScheduleRepository.get(ctx).getRange(semester.startIso, semester.endIso);
        long now = System.currentTimeMillis();

        int total = 0, done = 0;
        Map<String, Stat> map = new LinkedHashMap<>();
        for (Schedule s : list) {
            int p = s.periods();
            total += p;
            if (DateUtil.toMillis(s.date, s.endMin) <= now) done += p;
            String key = DateUtil.fold(s.subject);
            Stat st = map.get(key);
            if (st == null) {
                st = new Stat();
                st.name = s.subject;
                st.status = s.status;
                map.put(key, st);
            }
            st.periods += p;
            if (rank(s.status) > rank(st.status)) st.status = s.status;
            if (s.hasNote()) st.note = s.note.trim(); // list đã sắp theo ngày: ghi chú sau cùng là mới nhất
        }
        stats = new ArrayList<>(map.values());
        Collections.sort(stats, (a, b) -> a.periods != b.periods ? b.periods - a.periods : a.name.compareToIgnoreCase(b.name));
        maxPeriods = stats.isEmpty() ? 1 : stats.get(0).periods;

        float percent = total == 0 ? 0f : done * 100f / total;
        tvSemester.setText(semester.label());
        tvTotal.setText(String.valueOf(total));
        tvSubjects.setText(String.valueOf(stats.size()));
        tvPercent.setText(String.format(new Locale("vi", "VN"), "%.1f%%", percent));
        tvDone.setText(String.valueOf(done));
        tvDoneOf.setText("/ " + total + " tiết đã học");
        pb.setProgress(Math.round(percent * 10));
        tvRemaining.setText("Còn " + (total - done) + " tiết · " + Semester.WEEKS + " tuần trong học kỳ");
        tvUpdated.setText("Cập nhật đến " + DateUtil.hm(DateUtil.nowMin()) + " · " + DateUtil.dmy(Calendar.getInstance()));
        renderBars();
    }

    private void renderBars() {
        if (!isAdded()) return;
        Context ctx = requireContext();
        String q = DateUtil.fold(etSearch.getText().toString());
        bars.removeAllViews();
        int shown = 0;
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (Stat s : stats) {
            if (!q.isEmpty() && !DateUtil.fold(s.name).contains(q)) continue;
            View row = inf.inflate(R.layout.item_subject_bar, bars, false);
            ((TextView) row.findViewById(R.id.tv_name)).setText(s.name);
            ProgressBar p = row.findViewById(R.id.pb_bar);
            p.setMax(maxPeriods);
            p.setProgress(s.periods);
            ((TextView) row.findViewById(R.id.tv_count)).setText(String.valueOf(s.periods));
            StatusStyle.apply((TextView) row.findViewById(R.id.tv_status), s.status);
            TextView note = row.findViewById(R.id.tv_note);
            if (s.note != null && !s.note.isEmpty()) {
                note.setText(s.note);
                note.setVisibility(View.VISIBLE);
            }
            bars.addView(row);
            shown++;
        }
        if (shown == 0) {
            tvNoData.setVisibility(View.VISIBLE);
            tvNoData.setText(stats.isEmpty() ? "Chưa có lịch học trong học kỳ này." : "Không tìm thấy môn học phù hợp.");
        } else {
            tvNoData.setVisibility(View.GONE);
        }
    }

    private void export(boolean pdf) {
        Context ctx = requireContext();
        List<Schedule> list = ScheduleRepository.get(ctx).getRange(semester.startIso, semester.endIso);
        if (list.isEmpty()) {
            Toast.makeText(ctx, "Chưa có dữ liệu để xuất trong học kỳ này", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = pdf ? ExportUtil.exportPdf(ctx, semester.label(), list) : ExportUtil.exportCsv(ctx, semester.label(), list);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(pdf ? "application/pdf" : "text/csv");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri("", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, pdf ? "Xuất PDF" : "Xuất Excel (CSV)"));
        } catch (Exception e) {
            Toast.makeText(ctx, "Không thể xuất dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
