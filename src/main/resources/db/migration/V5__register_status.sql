-- Preserve registration and attendance history; existing registrations remain active.
ALTER TABLE register ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE register ADD CONSTRAINT ck_register_status CHECK (status IN ('ACTIVE', 'CANCELED'));
