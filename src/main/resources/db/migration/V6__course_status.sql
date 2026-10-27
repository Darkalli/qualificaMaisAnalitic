ALTER TABLE course
    ADD COLUMN status_course VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL;

ALTER TABLE course
    ADD CONSTRAINT ck_course_status
        CHECK (status_course IN ('ACTIVE', 'CANCELED', 'CLOSED'));