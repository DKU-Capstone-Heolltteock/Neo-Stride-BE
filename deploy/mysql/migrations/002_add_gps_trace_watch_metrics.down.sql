-- Roll back nullable watch metrics on GPS traces.
ALTER TABLE gps_traces
    DROP COLUMN cadence,
    DROP COLUMN heart_rate;
