-- Add nullable watch metrics to each GPS trace.
-- Existing rows receive NULL for both columns by default.
ALTER TABLE gps_traces
    ADD COLUMN heart_rate DECIMAL(5, 1) NULL AFTER recorded_time,
    ADD COLUMN cadence DECIMAL(5, 1) NULL AFTER heart_rate;
