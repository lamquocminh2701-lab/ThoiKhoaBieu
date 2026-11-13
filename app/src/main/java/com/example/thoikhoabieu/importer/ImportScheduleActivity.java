package com.example.thoikhoabieu.importer;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.thoikhoabieu.R;
import com.example.thoikhoabieu.data.ScheduleRepository;
import com.example.thoikhoabieu.model.Schedule;
import com.example.thoikhoabieu.util.DateUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Màn hình nhập thời khóa biểu hàng loạt từ tệp CSV:
 * - Trạng thái 1: Chọn tệp tin từ bộ nhớ máy (Storage Access Framework).
 * - Trạng thái 2: Xem trước dữ liệu, phân loại Hợp lệ / Trùng lịch / Lỗi định dạng.
 * - Trạng thái 3: Hộp thoại xác nhận trước khi lưu vào SQLite.
 * - Trạng thái 4: Báo cáo kết quả nhập dữ liệu chi tiết.
 */
public class ImportScheduleActivity extends AppCompatActivity {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_VALID = 1;
    public static final int FILTER_CONFLICT = 2;
    public static final int FILTER_ERROR = 3;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View layoutPick, layoutPreview, layoutResult;
    private TextView tvTopTitle, tvTopSubtitle;
    private TextView tvPreviewFilename, tvStatTotal, tvStatValid, tvStatConflict, tvStatError;
    private TextView chipAll, chipValid, chipConflict, chipError;
    private LinearLayout previewListContainer;
    private MaterialButton btnCommitImport;

    // View cho kết quả
    private TextView tvResultTitle, tvResultDesc, tvResultSummaryLines;

    private String currentFileName = "";
    private final List<ImportPreviewItem> previewItems = new ArrayList<>();
    private int currentFilter = FILTER_ALL;
    private ScheduleRepository repo;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFileSelected);

    private final ActivityResultLauncher<String> saveSampleLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), this::onSaveSampleResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_schedule);
        repo = ScheduleRepository.get(this);

        initViews();
        setupEvents();
        showPickState();
    }

    private void initViews() {
        layoutPick = findViewById(R.id.layout_pick);
        layoutPreview = findViewById(R.id.layout_preview);
        layoutResult = findViewById(R.id.layout_result);

        tvTopTitle = findViewById(R.id.tv_top_title);
        tvTopSubtitle = findViewById(R.id.tv_top_subtitle);

        tvPreviewFilename = findViewById(R.id.tv_preview_filename);
        tvStatTotal = findViewById(R.id.tv_stat_total);
        tvStatValid = findViewById(R.id.tv_stat_valid);
        tvStatConflict = findViewById(R.id.tv_stat_conflict);
        tvStatError = findViewById(R.id.tv_stat_error);

        chipAll = findViewById(R.id.chip_filter_all);
        chipValid = findViewById(R.id.chip_filter_valid);
        chipConflict = findViewById(R.id.chip_filter_conflict);
        chipError = findViewById(R.id.chip_filter_error);

        previewListContainer = findViewById(R.id.preview_list_container);
        btnCommitImport = findViewById(R.id.btn_commit_import);

        tvResultTitle = findViewById(R.id.tv_result_title);
        tvResultDesc = findViewById(R.id.tv_result_desc);
        tvResultSummaryLines = findViewById(R.id.tv_result_summary_lines);
    }

    private void setupEvents() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Nút chọn tệp
        findViewById(R.id.btn_choose_file).setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{"text/*", "*/*"});
        });

        // Nút lưu file CSV mẫu về máy
        findViewById(R.id.btn_save_sample_csv).setOnClickListener(v -> saveSampleCsv());

        // Nút chia sẻ file CSV mẫu
        findViewById(R.id.btn_share_sample_csv).setOnClickListener(v -> shareSampleCsv());

        // Nút lọc danh sách preview
        chipAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        chipValid.setOnClickListener(v -> setFilter(FILTER_VALID));
        chipConflict.setOnClickListener(v -> setFilter(FILTER_CONFLICT));
        chipError.setOnClickListener(v -> setFilter(FILTER_ERROR));

        // Nút bấm nhập các dòng đã chọn
        btnCommitImport.setOnClickListener(v -> confirmAndCommitImport());

        findViewById(R.id.btn_rechoose).setOnClickListener(v -> showPickState());
        findViewById(R.id.btn_cancel_preview).setOnClickListener(v -> finish());

        findViewById(R.id.btn_finish_to_schedule).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        findViewById(R.id.btn_import_another).setOnClickListener(v -> showPickState());
    }

    private void showPickState() {
        layoutPick.setVisibility(View.VISIBLE);
        layoutPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        tvTopTitle.setText("Nhập thời khóa biểu");
        tvTopSubtitle.setText("Tự động nạp lịch từ tệp CSV");
    }

    private void showPreviewState() {
        layoutPick.setVisibility(View.GONE);
        layoutPreview.setVisibility(View.VISIBLE);
        layoutResult.setVisibility(View.GONE);
        tvTopTitle.setText("Xem trước dữ liệu");
        tvTopSubtitle.setText("Kiểm tra và xác nhận các buổi học");
    }

    private void showResultState() {
        layoutPick.setVisibility(View.GONE);
        layoutPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        tvTopTitle.setText("Kết quả nhập lịch");
        tvTopSubtitle.setText("Báo cáo số lượng bản ghi đã xử lý");
    }

    private void onFileSelected(Uri uri) {
        if (uri == null) return;

        currentFileName = getFileNameFromUri(uri);
        long fileSize = getFileSizeFromUri(uri);

        // Hiển thị dialog đang đọc
        MaterialAlertDialogBuilder progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Đang phân tích tệp")
                .setMessage("Đang đọc và kiểm tra xung đột dữ liệu...")
                .setCancelable(false);
        androidx.appcompat.app.AlertDialog dialog = progressDialog.show();

        executor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        Toast.makeText(this, "Không thể mở tệp tin đã chọn.", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                ScheduleCsvParser.ParseResult parseResult = ScheduleCsvParser.parse(is, fileSize);
                if (!parseResult.success) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        showErrorDialog("Lỗi đọc tệp CSV", parseResult.errorMessage);
                    });
                    return;
                }

                // Chuyển đổi và kiểm tra hợp lệ
                List<ImportPreviewItem> items = new ArrayList<>();
                for (ScheduleCsvParser.CsvRow row : parseResult.dataRows) {
                    ImportPreviewItem item = ImportValidator.validateFormat(row, parseResult.headerIndexMap);
                    items.add(item);
                }

                // Kiểm tra xung đột và trùng lặp
                ImportValidator.checkConflictsAndDuplicates(items, repo);

                mainHandler.post(() -> {
                    dialog.dismiss();
                    previewItems.clear();
                    previewItems.addAll(items);
                    displayPreviewData();
                    showPreviewState();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    dialog.dismiss();
                    showErrorDialog("Lỗi không mong muốn", "Đã xảy ra lỗi khi đọc tệp: " + e.getMessage());
                });
            }
        });
    }

    private void displayPreviewData() {
        tvPreviewFilename.setText(currentFileName.isEmpty() ? "Tệp dữ liệu CSV" : currentFileName);

        int total = previewItems.size();
        int valid = 0;
        int conflict = 0;
        int error = 0;

        for (ImportPreviewItem item : previewItems) {
            if (item.isValid()) valid++;
            else if (item.isConflict() || item.isDuplicate()) conflict++;
            else if (item.isError()) error++;
        }

        tvStatTotal.setText(String.valueOf(total));
        tvStatValid.setText(String.valueOf(valid));
        tvStatConflict.setText(String.valueOf(conflict));
        tvStatError.setText(String.valueOf(error));

        setFilter(FILTER_ALL);
        updateCommitButtonText();
    }

    private void setFilter(int filter) {
        currentFilter = filter;
        styleFilterChip(chipAll, filter == FILTER_ALL);
        styleFilterChip(chipValid, filter == FILTER_VALID);
        styleFilterChip(chipConflict, filter == FILTER_CONFLICT);
        styleFilterChip(chipError, filter == FILTER_ERROR);

        renderFilteredList();
    }

    private void styleFilterChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.primary : R.color.text_primary));
        chip.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void renderFilteredList() {
        previewListContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);

        int shownCount = 0;
        for (ImportPreviewItem item : previewItems) {
            boolean match = false;
            switch (currentFilter) {
                case FILTER_ALL:
                    match = true;
                    break;
                case FILTER_VALID:
                    match = item.isValid();
                    break;
                case FILTER_CONFLICT:
                    match = item.isConflict() || item.isDuplicate();
                    break;
                case FILTER_ERROR:
                    match = item.isError();
                    break;
            }

            if (!match) continue;

            View card = inf.inflate(R.layout.item_import_preview, previewListContainer, false);
            CheckBox cb = card.findViewById(R.id.cb_select);
            TextView tvLine = card.findViewById(R.id.tv_line_num);
            TextView tvBadge = card.findViewById(R.id.tv_badge_status);
            TextView tvSubject = card.findViewById(R.id.tv_subject);
            TextView tvDateTime = card.findViewById(R.id.tv_datetime);
            TextView tvSubInfo = card.findViewById(R.id.tv_sub_info);
            TextView tvMessage = card.findViewById(R.id.tv_desc_message);

            tvLine.setText("Dòng " + item.lineNumber);

            if (item.schedule != null) {
                tvSubject.setText(item.schedule.subject);
                Calendar c = DateUtil.parse(item.schedule.date);
                tvDateTime.setText(item.schedule.timeRange() + "  ·  " + DateUtil.dowFull(c) + ", " + DateUtil.dmy(c));
                String roomClass = "Phòng " + (item.schedule.room.isEmpty() ? "—" : item.schedule.room)
                        + "  ·  Lớp " + (item.schedule.className.isEmpty() ? "—" : item.schedule.className)
                        + "  ·  " + Schedule.statusLabel(item.schedule.status);
                tvSubInfo.setText(roomClass);
                tvSubInfo.setVisibility(View.VISIBLE);
            } else {
                tvSubject.setText("(Lỗi định dạng dòng)");
                tvSubject.setTextColor(ContextCompat.getColor(this, R.color.danger));
                tvDateTime.setText(item.rawPreview);
                tvSubInfo.setVisibility(View.GONE);
            }

            // Thiết lập huy hiệu & thông điệp
            switch (item.statusType) {
                case VALID:
                    tvBadge.setText("✓ Sẵn sàng");
                    tvBadge.setBackgroundResource(R.drawable.bg_status_done);
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.success));
                    tvMessage.setText(item.message);
                    tvMessage.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    tvMessage.setBackgroundResource(R.drawable.bg_note);
                    cb.setEnabled(true);
                    break;
                case DUPLICATE_DB:
                    tvBadge.setText("↺ Đã có");
                    tvBadge.setBackgroundResource(R.drawable.bg_status_pause);
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_pause_text));
                    tvMessage.setText(item.message);
                    tvMessage.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    tvMessage.setBackgroundResource(R.drawable.bg_cell);
                    cb.setEnabled(true);
                    break;
                case CONFLICT_DB:
                case CONFLICT_FILE:
                    tvBadge.setText("⚠ Trùng lịch");
                    tvBadge.setBackgroundResource(R.drawable.bg_status_active);
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.warn_text));
                    tvMessage.setText(item.message);
                    tvMessage.setTextColor(ContextCompat.getColor(this, R.color.danger));
                    tvMessage.setBackgroundResource(R.drawable.bg_warn);
                    cb.setEnabled(true);
                    break;
                case INVALID_FORMAT:
                default:
                    tvBadge.setText("✕ Lỗi tệp");
                    tvBadge.setBackgroundResource(R.drawable.bg_conflict);
                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.danger));
                    tvMessage.setText(item.message);
                    tvMessage.setTextColor(ContextCompat.getColor(this, R.color.danger));
                    tvMessage.setBackgroundResource(R.drawable.bg_warn);
                    cb.setEnabled(false);
                    item.willImport = false;
                    break;
            }

            cb.setChecked(item.willImport);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.willImport = isChecked;
                updateCommitButtonText();
            });

            previewListContainer.addView(card);
            shownCount++;
        }

        if (shownCount == 0) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Không có mục nào phù hợp với bộ lọc đã chọn.");
            emptyTv.setPadding(0, 40, 0, 40);
            emptyTv.setGravity(android.view.Gravity.CENTER);
            emptyTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            previewListContainer.addView(emptyTv);
        }
    }

    private int getSelectedCount() {
        int count = 0;
        for (ImportPreviewItem item : previewItems) {
            if (item.willImport && item.schedule != null) count++;
        }
        return count;
    }

    private void updateCommitButtonText() {
        int count = getSelectedCount();
        btnCommitImport.setText(count > 0 
                ? ("Nhập " + count + " buổi học đã chọn") 
                : "Chưa chọn buổi học nào");
        btnCommitImport.setEnabled(count > 0);
    }

    private void confirmAndCommitImport() {
        final List<Schedule> toImport = new ArrayList<>();
        for (ImportPreviewItem item : previewItems) {
            if (item.willImport && item.schedule != null) {
                toImport.add(item.schedule);
            }
        }

        if (toImport.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất một buổi học hợp lệ để nhập.", Toast.LENGTH_SHORT).show();
            return;
        }

        int total = previewItems.size();
        int valid = toImport.size();
        int skipped = total - valid;

        String msg = String.format(Locale.US,
                "Tệp có %d buổi học.\n\n• Số buổi được chọn để nhập: %d\n• Số buổi bỏ qua (trùng/lỗi/bỏ chọn): %d\n\nBạn có chắc chắn muốn lưu %d buổi học này vào cơ sở dữ liệu?",
                total, valid, skipped, valid);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Xác nhận nhập lịch học")
                .setMessage(msg)
                .setPositiveButton("Xác nhận nhập", (d, which) -> executeBatchImport(toImport))
                .setNegativeButton("Xem lại", null)
                .show();
    }

    private void executeBatchImport(List<Schedule> toImport) {
        MaterialAlertDialogBuilder progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Đang lưu dữ liệu")
                .setMessage("Đang ghi vào SQLite và đồng bộ thông báo...")
                .setCancelable(false);
        androidx.appcompat.app.AlertDialog dialog = progressDialog.show();

        repo.importBatch(toImport, result -> {
            dialog.dismiss();
            if (result.ok()) {
                int total = previewItems.size();
                int imported = toImport.size();
                int dup = 0, conf = 0, err = 0;

                for (ImportPreviewItem item : previewItems) {
                    if (item.isDuplicate()) dup++;
                    else if (item.isConflict()) conf++;
                    else if (item.isError()) err++;
                }

                tvResultTitle.setText("Nhập thời khóa biểu thành công!");
                tvResultDesc.setText("Đã lưu an toàn " + imported + " buổi học vào cơ sở dữ liệu SQLite.");

                String summary = String.format(Locale.US,
                        "• Tổng số dòng trong tệp: %d\n• Nhập thành công: %d buổi học\n• Số buổi trùng khung giờ: %d buổi (đã lưu, có thể chọn ưu tiên)\n• Bỏ qua do trùng lặp hoàn toàn: %d buổi\n• Dòng bị lỗi định dạng: %d dòng\n• Hệ thống nhắc lịch học: Đã cập nhật tự động",
                        total, imported, conf, dup, err);
                tvResultSummaryLines.setText(summary);

                showResultState();
            } else {
                showErrorDialog("Lỗi lưu dữ liệu", result.message);
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "thoi_khoa_bieu.csv";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private long getFileSizeFromUri(Uri uri) {
        long size = 0;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return size;
    }

    private void onSaveSampleResult(Uri uri) {
        if (uri != null) {
            writeSampleCsvToUri(uri);
        }
        // Người dùng hủy chọn vị trí: không làm gì cả, không báo lỗi, không báo thành công giả
    }

    private void saveSampleCsv() {
        try {
            saveSampleLauncher.launch("thoi_khoa_bieu_mau.csv");
        } catch (ActivityNotFoundException e) {
            saveSampleToFallbackStorage();
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở trình lưu tệp: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeSampleCsvToUri(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                Toast.makeText(this, "Không thể mở luồng ghi vào vị trí đã chọn.", Toast.LENGTH_LONG).show();
                return;
            }
            try (OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                w.write(getSampleCsvContent());
                w.flush();
            }
            Toast.makeText(this, "Đã lưu tệp CSV mẫu thành công!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi lưu tệp CSV mẫu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveSampleToFallbackStorage() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            File destFile = new File(downloadsDir, "thoi_khoa_bieu_mau.csv");
            try (FileOutputStream fos = new FileOutputStream(destFile);
                 OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                w.write(getSampleCsvContent());
                w.flush();
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Đã lưu tệp mẫu (Dự phòng)")
                    .setMessage("Thiết bị thiếu trình chọn thư mục hệ thống (DocumentsUI).\nTệp CSV mẫu đã được lưu trực tiếp tại:\n"
                            + destFile.getAbsolutePath())
                    .setPositiveButton("Đã hiểu", null)
                    .show();
        } catch (Exception ex) {
            try {
                File fallback = new File(getCacheDir(), "thoi_khoa_bieu_mau.csv");
                try (FileOutputStream fos = new FileOutputStream(fallback);
                     OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    w.write(getSampleCsvContent());
                    w.flush();
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Thông báo")
                        .setMessage("Thiết bị không hỗ trợ lưu tệp ngoài.\nTệp mẫu được lưu tại bộ nhớ tạm của ứng dụng:\n"
                                + fallback.getAbsolutePath())
                        .setPositiveButton("Đã hiểu", null)
                        .show();
            } catch (Exception e) {
                Toast.makeText(this, "Không thể lưu tệp mẫu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void shareSampleCsv() {
        try {
            File dir = new File(getCacheDir(), "samples");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File sampleFile = new File(dir, "thoi_khoa_bieu_mau.csv");
            try (FileOutputStream fos = new FileOutputStream(sampleFile);
                 OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                w.write(getSampleCsvContent());
                w.flush();
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", sampleFile);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/csv");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri("thoi_khoa_bieu_mau.csv", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Kiểm tra chủ động xem thiết bị có ứng dụng nào nhận ACTION_SEND với text/csv hay không
            PackageManager pm = getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(send, 0);
            if (activities == null || activities.isEmpty()) {
                Toast.makeText(this,
                        "Thiết bị chưa có ứng dụng hỗ trợ chia sẻ tệp. Bạn vẫn có thể sử dụng nút Lưu tệp CSV mẫu.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Intent chooser = Intent.createChooser(send, "Chia sẻ CSV mẫu");
            startActivity(chooser);

        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "Thiết bị chưa có ứng dụng hỗ trợ chia sẻ tệp. Bạn vẫn có thể sử dụng nút Lưu tệp CSV mẫu.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không thể chia sẻ tệp: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getSampleCsvContent() {
        return "\uFEFF"
                + "Ngày,Thứ,Bắt đầu,Kết thúc,Số tiết,Môn học,Lớp,Phòng,Trạng thái,Ghi chú\n"
                + "24/08/2026,Thứ Hai,07:00,08:30,2,Lập trình thiết bị di động,21DTH01,A2-301,Đang học,\"Mang laptop, máy ảo Android\"\n"
                + "24/08/2026,Thứ Hai,08:45,10:15,2,Mạng máy tính,21DTH01,B1-204,Đang học,Thực hành lab mạng\n"
                + "25/08/2026,Thứ Ba,07:00,08:30,2,Toán rời rạc,21DTH02,C3-102,Đang học,Bài tập logic mệnh đề\n"
                + "25/08/2026,Thứ Ba,09:00,10:30,2,\"Chuyên đề \"\"Kỹ nghệ dữ liệu lớn\"\"\",21DTH01,A2-205,Đang học,Thuyết trình nhóm\n"
                + "26/08/2026,Thứ Tư,13:30,15:00,2,Cơ sở dữ liệu nâng cao,21DTH03,Lab 04,Đang học,Thực hành SQL tối ưu\n";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
