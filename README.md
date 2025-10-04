
---

## 🗃️ Tóm tắt schema

- `locations` – lưu toạ độ, timezone, cờ `is_current_location`
- `favorites` – danh sách yêu thích (có `sort_order`)
- `search_history` – lịch sử gõ tìm kiếm
- `weather_current` – thời tiết hiện tại (1 dòng/location, `ON CONFLICT REPLACE`)
- `weather_hourly` – dự báo theo giờ (unique theo `location_id, ts`)
- `weather_daily` – dự báo theo ngày (unique theo `location_id, date_ts`)
- `alert_rules`, `alert_events` – quy tắc & log cảnh báo
- `app_settings` – cặp key/value cài đặt app
- `api_cache` – cache raw JSON (nếu muốn lưu phản hồi API)
- Views: `v_favorites_current`, `v_hourly_upcoming`, `v_daily_upcoming`

> Gốc thời gian dùng **epoch seconds**; bật **FOREIGN KEY** bằng PRAGMA.

---

## 🚀 Chạy dự án

### Yêu cầu
- Android Studio mới nhất
- JDK 11 hoặc 17
- AVD **Android 13/14 (API 33/34 – Google APIs, x86_64)** khuyến nghị

### Bước chạy nhanh
1. Mở project bằng Android Studio.
2. Tạo AVD (Tools → Device Manager) → Pixel 6/7, API 33+.
3. **Run (▶)** để cài app.
- Lần đầu mở app, DB sẽ được tạo từ `assets/sql/weather_schema.sql`.
4. Mở **View → Tool Windows → App Inspection → Databases** để xem `weather.db`.

> Nếu dùng **Debug (🐞)** và app đứng ở log “Waiting for debugger…”, hãy chạy bằng **Run (▶)** hoặc tắt “Wait for debugger” trong Developer Options của emulator.

---

## 🧪 Lưu ý về FTS5

Schema có bảng FTS5 `places_fts`. Một số emulator/ROM không bật module `fts5`, khi đó:
- Code đã bọc `CREATE VIRTUAL TABLE … fts5` trong `try/catch` để **không crash**.
- Nếu muốn **bật FTS5 thật**:
- Dùng emulator **API 33/34 – Google APIs (x86_64)**, **Cold Boot** lại.
- Hoặc comment 2 dòng FTS5 trong `weather_schema.sql` nếu không dùng search offline.

---

## 🔧 Câu lệnh Gradle

Build debug:
```bash
./gradlew assembleDebug
