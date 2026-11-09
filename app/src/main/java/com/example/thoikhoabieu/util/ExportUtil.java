package com.example.thoikhoabieu.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import com.example.thoikhoabieu.model.Schedule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ExportUtil {
    private ExportUtil() {
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "exports");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static Uri uri(Context ctx, File f) {
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /** CSV UTF-8 có BOM để Excel hiển thị đúng tiếng Việt. */
    public static Uri exportCsv(Context ctx, String label, List<Schedule> list) throws IOException {
        File f = new File(dir(ctx), "thoi_khoa_bieu.csv");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write(csv(label) + "\n");
            w.write("Ngày,Thứ,Bắt đầu,Kết thúc,Số tiết,Môn học,Lớp,Phòng,Trạng thái,Ghi chú\n");
            for (Schedule s : list) {
                java.util.Calendar c = DateUtil.parse(s.date);
                w.write(DateUtil.dmy(c) + "," + DateUtil.dowFull(c) + "," + DateUtil.hm(s.startMin) + "," + DateUtil.hm(s.endMin)
                        + "," + s.periods() + "," + csv(s.subject) + "," + csv(s.className) + "," + csv(s.room) + "," + csv(Schedule.statusLabel(s.status)) + "," + csv(s.note) + "\n");
            }
        }
        return uri(ctx, f);
    }

    public static Uri exportPdf(Context ctx, String label, List<Schedule> list) throws IOException {
        final int w = 595, h = 842;
        final String[] heads = {"Ngày", "Thứ", "Giờ", "Môn học", "Lớp", "Phòng", "Tiết", "Trạng thái"};
        final float[] xs = {40, 106, 132, 204, 316, 380, 424, 452};
        final float[] ws = {62, 22, 68, 108, 60, 40, 24, 103};
        final int STATUS_COL = 7;

        File f = new File(dir(ctx), "thoi_khoa_bieu.pdf");
        PdfDocument doc = new PdfDocument();
        try {
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTextSize(10f);
            TextPaint head = new TextPaint(tp);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            TextPaint title = new TextPaint(tp);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(18f);
            Paint line = new Paint();
            line.setColor(0xFFCCCCCC);
            // màu trạng thái giống huy hiệu trong app
            TextPaint stActive = new TextPaint(tp);
            stActive.setColor(0xFFB45309);
            TextPaint stDone = new TextPaint(tp);
            stDone.setColor(0xFF1E8E3E);
            TextPaint stPause = new TextPaint(tp);
            stPause.setColor(0xFF5F6B7F);

            int nActive = 0, nDone = 0, nPause = 0;
            for (Schedule s : list) {
                if (Schedule.HOAN_THANH.equals(s.status)) nDone++;
                else if (Schedule.TAM_NGHI.equals(s.status)) nPause++;
                else nActive++;
            }

            int pageNo = 1;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(w, h, pageNo).create());
            Canvas cv = page.getCanvas();
            cv.drawText("THỜI KHÓA BIỂU", 40, 52, title);
            cv.drawText(label + " · Xuất lúc " + new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.US).format(new Date()), 40, 72, tp);
            cv.drawText(list.size() + " buổi · " + Schedule.statusLabel(Schedule.DANG_HOC) + ": " + nActive
                    + " · " + Schedule.statusLabel(Schedule.HOAN_THANH) + ": " + nDone
                    + " · " + Schedule.statusLabel(Schedule.TAM_NGHI) + ": " + nPause, 40, 90, tp);
            float y = drawHead(cv, head, line, heads, xs, 114);

            for (Schedule s : list) {
                if (y > h - 50) {
                    doc.finishPage(page);
                    page = doc.startPage(new PdfDocument.PageInfo.Builder(w, h, ++pageNo).create());
                    cv = page.getCanvas();
                    y = drawHead(cv, head, line, heads, xs, 50);
                }
                java.util.Calendar c = DateUtil.parse(s.date);
                String[] cells = {DateUtil.dmy(c), DateUtil.dowShort(c), s.timeRange(), s.subject, s.className, s.room, String.valueOf(s.periods()), Schedule.statusLabel(s.status)};
                TextPaint statusPaint = Schedule.HOAN_THANH.equals(s.status) ? stDone
                        : Schedule.TAM_NGHI.equals(s.status) ? stPause : stActive;
                for (int i = 0; i < cells.length; i++) {
                    TextPaint paint = i == STATUS_COL ? statusPaint : tp;
                    CharSequence t = TextUtils.ellipsize(cells[i], paint, ws[i], TextUtils.TruncateAt.END);
                    cv.drawText(t.toString(), xs[i], y, paint);
                }
                y += 6;
                cv.drawLine(40, y, w - 40, y, line);
                y += 15;
            }
            doc.finishPage(page);
            try (FileOutputStream fo = new FileOutputStream(f)) {
                doc.writeTo(fo);
            }
        } finally {
            doc.close();
        }
        return uri(ctx, f);
    }

    private static float drawHead(Canvas cv, TextPaint head, Paint line, String[] heads, float[] xs, float y) {
        for (int i = 0; i < heads.length; i++) cv.drawText(heads[i], xs[i], y, head);
        cv.drawLine(40, y + 6, 555, y + 6, line);
        return y + 24;
    }
}
