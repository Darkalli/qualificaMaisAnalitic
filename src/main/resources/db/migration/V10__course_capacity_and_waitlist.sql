ALTER TABLE course ADD COLUMN maximum_students_count INTEGER DEFAULT 20 NOT NULL;

-- Keep every existing active enrollment within the initial capacity.
UPDATE course
SET maximum_students_count = (
    SELECT COUNT(*) FROM register r WHERE r.course_id = course.id AND r.status = 'ACTIVE'
)
WHERE (SELECT COUNT(*) FROM register r WHERE r.course_id = course.id AND r.status = 'ACTIVE') > 20;

ALTER TABLE course ADD CONSTRAINT ck_course_capacity CHECK (maximum_students_count > 0);
ALTER TABLE register DROP CONSTRAINT ck_register_status;
ALTER TABLE register ADD CONSTRAINT ck_register_status CHECK (status IN ('ACTIVE', 'CANCELED', 'WAITLISTED'));
