-- Preserve existing registration dates at midnight and retain times for new registrations.
ALTER TABLE register ALTER COLUMN register_date TYPE TIMESTAMP(6);
