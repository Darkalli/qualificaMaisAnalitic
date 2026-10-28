-- Validate every legacy value BEFORE modifying rows. Unknown/blank sessions must be
-- corrected explicitly by the operator, never inferred from class times or replaced
-- by an arbitrary period. This first statement also avoids partial normalization on
-- databases such as H2 whose DDL is not transactional; PostgreSQL rolls back V8.
-- Preserve the existing VARCHAR(255), NOT NULL and all other class data.
ALTER TABLE course_class ADD CONSTRAINT ck_class_session_legacy_supported
    CHECK (UPPER(TRIM(session)) IN ('MORNING', 'MANHÃ', 'AFTERNOON', 'TARDE', 'NIGHT', 'NOITE'));

UPDATE course_class
SET session = CASE UPPER(TRIM(session))
    WHEN 'MORNING' THEN 'MORNING'
    WHEN 'MANHÃ' THEN 'MORNING'
    WHEN 'AFTERNOON' THEN 'AFTERNOON'
    WHEN 'TARDE' THEN 'AFTERNOON'
    WHEN 'NIGHT' THEN 'NIGHT'
    WHEN 'NOITE' THEN 'NIGHT'
    ELSE session
END;

ALTER TABLE course_class ADD CONSTRAINT ck_class_session
    CHECK (session IN ('MORNING', 'AFTERNOON', 'NIGHT'));
ALTER TABLE course_class DROP CONSTRAINT ck_class_session_legacy_supported;
