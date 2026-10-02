-- Dados existentes precisam ter sessão e horários preenchidos antes desta migração.
ALTER TABLE course_class ALTER COLUMN session SET NOT NULL;
ALTER TABLE course_class ALTER COLUMN start SET NOT NULL;
ALTER TABLE course_class ALTER COLUMN finish SET NOT NULL;
