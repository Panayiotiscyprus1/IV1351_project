-- TRIGGERS wiring the functions to the tables

-- A) Keep a single "current" course_layout per course_code
DROP TRIGGER IF EXISTS set_current_layout ON course_layout;
CREATE TRIGGER set_current_layout
BEFORE INSERT ON course_layout
FOR EACH ROW
EXECUTE FUNCTION trg_set_current_layout();

-- B) Enforce max-4-instances-per-period-per-teacher
DROP TRIGGER IF EXISTS check_max4_allocations ON allocations;
CREATE TRIGGER check_max4_allocations
BEFORE INSERT ON allocations
FOR EACH ROW
EXECUTE FUNCTION trg_check_max4_allocations();
