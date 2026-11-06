-- Seconds of audio each phone used per month (month is "2026-11", UTC).
CREATE TABLE IF NOT EXISTS usage (
  device TEXT NOT NULL,
  month TEXT NOT NULL,
  seconds REAL NOT NULL,
  PRIMARY KEY (device, month)
);
