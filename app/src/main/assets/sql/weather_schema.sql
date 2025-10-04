-- WEATHER APP - SQLite schema
-- Lưu ý: BẬT FK, dùng epoch giây
PRAGMA foreign_keys = ON;
PRAGMA user_version = 1;

BEGIN TRANSACTION;

-- =========================
-- 1) ĐỊA ĐIỂM & TÌM KIẾM
-- =========================
DROP TABLE IF EXISTS locations;
CREATE TABLE locations (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                TEXT NOT NULL,           -- "Hồ Chí Minh"
    country             TEXT,                    -- "VN"
    admin1              TEXT,                    -- "Ho Chi Minh"
    admin2              TEXT,                    -- quận/huyện nếu có
    lat                 REAL NOT NULL,
    lon                 REAL NOT NULL,
    timezone            TEXT,                    -- "Asia/Ho_Chi_Minh"
    external_id         TEXT,                    -- id từ OpenWeather/Geocoding (nếu muốn)
    is_current_location INTEGER NOT NULL DEFAULT 0, -- 1 nếu đây là vị trí hiện tại
    created_at          INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE(lat, lon) ON CONFLICT IGNORE
);
CREATE INDEX IF NOT EXISTS idx_locations_name ON locations(name);
CREATE INDEX IF NOT EXISTS idx_locations_country ON locations(country);

-- Lịch sử tìm kiếm (để suggest)
DROP TABLE IF EXISTS search_history;
CREATE TABLE search_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    query      TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
);
CREATE INDEX IF NOT EXISTS idx_search_history_time ON search_history(created_at DESC);

-- Yêu thích
DROP TABLE IF EXISTS favorites;
CREATE TABLE favorites (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    location_id  INTEGER NOT NULL,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    created_at   INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE(location_id),
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_favorites_order ON favorites(sort_order);

-- (Tùy chọn) Danh mục địa danh + FTS5 để search offline nhanh
DROP TABLE IF EXISTS places;
CREATE TABLE places (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,  -- "Ha Noi"
    country     TEXT,
    admin1      TEXT,
    lat         REAL NOT NULL,
    lon         REAL NOT NULL,
    source      TEXT            -- "geocoding-cache" | "builtin" | ...
);
-- FTS5 (Android hỗ trợ) để tìm kiếm tên (không bắt buộc)
DROP TABLE IF EXISTS places_fts;
CREATE VIRTUAL TABLE places_fts USING fts5(
    name, country, admin1, content=''
);
-- Có thể sync bằng trigger/cron trong app khi seed dữ liệu

-- =========================
-- 2) THỜI TIẾT: HIỆN TẠI
-- =========================
DROP TABLE IF EXISTS weather_current;
CREATE TABLE weather_current (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    location_id     INTEGER NOT NULL,
    obs_time        INTEGER NOT NULL,           -- epoch giây
    temp_c          REAL NOT NULL,
    feels_like_c    REAL,
    humidity_pct    REAL,
    wind_mps        REAL,
    wind_deg        REAL,
    visibility_km   REAL,
    pressure_hpa    REAL,
    uvi             REAL,
    clouds_pct      REAL,
    precip_mm       REAL,                        -- mưa hiện tại ước lượng
    condition_code  TEXT,                        -- mã trạng thái từ API
    condition_text  TEXT,                        -- "Partly Cloudy"
    icon_code       TEXT,                        -- "03d"...
    updated_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    UNIQUE(location_id) ON CONFLICT REPLACE,     -- luôn giữ bản ghi mới nhất/1 dòng per location
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_wc_loc ON weather_current(location_id);

-- =========================
-- 3) THỜI TIẾT THEO GIỜ (48h)
-- =========================
DROP TABLE IF EXISTS weather_hourly;
CREATE TABLE weather_hourly (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    location_id     INTEGER NOT NULL,
    ts              INTEGER NOT NULL,       -- epoch của giờ
    temp_c          REAL NOT NULL,
    humidity_pct    REAL,
    wind_mps        REAL,
    wind_deg        REAL,
    clouds_pct      REAL,
    pop_pct         REAL,                   -- xác suất mưa %
    precip_mm       REAL,                   -- lượng mưa dự báo
    uvi             REAL,
    pressure_hpa    REAL,
    condition_code  TEXT,
    condition_text  TEXT,
    icon_code       TEXT,
    UNIQUE(location_id, ts) ON CONFLICT REPLACE,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_wh_loc_ts ON weather_hourly(location_id, ts);

-- =========================
-- 4) THỜI TIẾT 7 NGÀY
-- =========================
DROP TABLE IF EXISTS weather_daily;
CREATE TABLE weather_daily (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    location_id     INTEGER NOT NULL,
    date_ts         INTEGER NOT NULL,       -- epoch 00:00 của ngày
    temp_min_c      REAL NOT NULL,
    temp_max_c      REAL NOT NULL,
    sunrise_ts      INTEGER,
    sunset_ts       INTEGER,
    moonrise_ts     INTEGER,
    moonset_ts      INTEGER,
    pop_pct         REAL,                   -- % mưa trong ngày
    precip_mm       REAL,                   -- mm dự kiến
    wind_mps        REAL,
    wind_deg        REAL,
    condition_code  TEXT,
    condition_text  TEXT,
    icon_code       TEXT,
    UNIQUE(location_id, date_ts) ON CONFLICT REPLACE,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_wd_loc_date ON weather_daily(location_id, date_ts);

-- =========================
-- 5) CẢNH BÁO THỜI TIẾT
-- =========================
DROP TABLE IF EXISTS alert_rules;
CREATE TABLE alert_rules (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,          -- "Nắng nóng > 36°C"
    location_id     INTEGER,                -- null = áp cho vị trí hiện tại
    metric          TEXT NOT NULL CHECK(metric IN ('temp','feels_like','wind','rain_prob','uvi','clouds')),
    op              TEXT NOT NULL CHECK(op IN ('>','>=','<','<=','==','!=')),
    threshold       REAL NOT NULL,
    threshold_unit  TEXT,                   -- "°C","m/s","%","UVI"
    active          INTEGER NOT NULL DEFAULT 1,
    rearm_minutes   INTEGER NOT NULL DEFAULT 60,  -- tối thiểu giữa 2 lần bắn
    created_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    updated_at      INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE SET NULL
);

DROP TABLE IF EXISTS alert_events;
CREATE TABLE alert_events (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_id        INTEGER NOT NULL,
    fired_at        INTEGER NOT NULL DEFAULT (strftime('%s','now')),
    observed_value  REAL,
    channel         TEXT,                   -- "local_notification"
    delivered       INTEGER NOT NULL DEFAULT 1,
    dismissed       INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(alert_id) REFERENCES alert_rules(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_alert_events_alert_time ON alert_events(alert_id, fired_at DESC);

-- =========================
-- 6) CÀI ĐẶT & CACHE
-- =========================
DROP TABLE IF EXISTS app_settings;
CREATE TABLE app_settings (
    key   TEXT PRIMARY KEY,   -- "temp_unit"
    value TEXT NOT NULL       -- "C" | "F"...
);

-- Seed mặc định theo UI "Cài đặt"
INSERT OR IGNORE INTO app_settings(key, value) VALUES
('temp_unit','C'),
('wind_unit','kmh'),
('theme','system'),
('language','vi'),
('cache_ttl_current_min','10'),
('cache_ttl_hourly_min','60'),
('cache_ttl_daily_min','180');

-- Cache thô (nếu muốn lưu JSON từ API để debug/offline)
DROP TABLE IF EXISTS api_cache;
CREATE TABLE api_cache (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    cache_key    TEXT NOT NULL UNIQUE,   -- ví dụ: "owm:onecall:10.77:106.69"
    json_body    TEXT NOT NULL,
    fetched_at   INTEGER NOT NULL,       -- epoch s
    ttl_seconds  INTEGER NOT NULL        -- 600, 3600...
);
CREATE INDEX IF NOT EXISTS idx_api_cache_time ON api_cache(fetched_at);

-- =========================
-- 7) VIEW PHỤC VỤ UI
-- =========================
DROP VIEW IF EXISTS v_favorites_current;
CREATE VIEW v_favorites_current AS
SELECT f.sort_order,
       l.id          AS location_id,
       l.name,
       l.country,
       wc.temp_c,
       wc.feels_like_c,
       wc.humidity_pct,
       (wc.wind_mps*3.6) AS wind_kmh,
       wc.visibility_km,
       wc.condition_text,
       wc.icon_code,
       wc.updated_at
FROM favorites f
JOIN locations l ON l.id = f.location_id
LEFT JOIN weather_current wc ON wc.location_id = l.id
ORDER BY f.sort_order ASC, l.name ASC;

DROP VIEW IF EXISTS v_hourly_upcoming;
CREATE VIEW v_hourly_upcoming AS
SELECT h.*
FROM weather_hourly h
WHERE h.ts >= strftime('%s','now') - 3600   -- giữ cả giờ trước
ORDER BY h.location_id, h.ts;

DROP VIEW IF EXISTS v_daily_upcoming;
CREATE VIEW v_daily_upcoming AS
SELECT d.*
FROM weather_daily d
WHERE d.date_ts >= (strftime('%s','now') - (strftime('%s','now') % 86400))  -- từ hôm nay
ORDER BY d.location_id, d.date_ts;

COMMIT;
