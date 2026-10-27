-- Existing courses keep an unknown period until it is explicitly provided.
ALTER TABLE course ADD COLUMN period VARCHAR(32);

ALTER TABLE course ADD CONSTRAINT ck_course_period
    CHECK (period IN ('MORNING', 'AFTERNOON', 'NIGHT'));
