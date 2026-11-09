# Thời khóa biểu (Android · Java · XML · SQLite)

Giao diện theo bản thiết kế Figma, có cột **trạng thái môn học**, lưu hoàn toàn bằng **SQLite trên máy** (offline, không cần máy chủ hay mạng).

## Mở project
Android Studio → **File ▸ Open** → chọn thư mục `TimetableApp` → đợi Gradle sync → chạy.

## Màn hình (4 tab)
| Tab | Nội dung |
|---|---|
| Trang chủ | Lịch **tuần** (thẻ ngày, hàng giờ, ghi chú, ô "Trống") và lịch **tháng** |
| Chi tiết | Lịch học hôm nay: ghi chú, trạng thái, khoảng trống, huy hiệu SẮP TỚI / ĐANG HỌC |
| Thống kê | Chọn học kỳ, tổng tiết, tiến độ, tra cứu môn, tiết theo môn kèm trạng thái và ghi chú, xuất CSV/PDF |
| Cài đặt | Nhắc trước 5/15/30 phút, âm thanh, rung, giờ yên lặng |

## Cơ sở dữ liệu
File `timetable.db`, bảng `schedule`:

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | |
| subject, class_name, room | TEXT NOT NULL | tên môn, lớp, phòng |
| date | TEXT NOT NULL | `yyyy-MM-dd` |
| start_min, end_min | INTEGER NOT NULL | phút trong ngày (09:00 = 540), `CHECK end_min > start_min` |
| note | TEXT | ghi chú |
| **status** | TEXT NOT NULL DEFAULT 'DANG_HOC' | `DANG_HOC` / `HOAN_THANH` / `TAM_NGHI` |

- Phiên bản DB = 2. Máy đã chạy bản SQLite đầu tiên (DB v1, chưa có `status`) sẽ được nâng cấp bằng `ALTER TABLE ... ADD COLUMN`, **không mất dữ liệu**.
- Nếu máy từng chạy bản dùng MongoDB, lần mở đầu tiên app tự nhập lịch học từ file bản sao `schedules_cache.json` (khi bảng còn trống).
- **Chặn trùng lịch**: `ScheduleRepository.insert/update` kiểm tra trùng (kèm `GAP = 1` phút nghỉ) trong cùng một transaction, nên dữ liệu trùng không thể được ghi. Form Thêm/Sửa còn cảnh báo ngay khi chọn giờ và gợi ý các khoảng trống.

## Cấu trúc mã
```
data/    ScheduleRepository (SQLite: đọc, ghi, kiểm tra trùng, khoảng trống)
model/   Schedule (có status), Semester
notify/  nhắc lịch (AlarmManager)
ui/      HomeFragment, DetailFragment, StatsFragment, SettingsFragment, StatusStyle
util/    DateUtil, Prefs, ExportUtil
```
