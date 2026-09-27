-- Academic years are limited to I-IV.
ALTER TABLE students DROP CONSTRAINT IF EXISTS students_academic_year_check;
ALTER TABLE students ADD CONSTRAINT students_academic_year_check CHECK (academic_year BETWEEN 1 AND 4);
