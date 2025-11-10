-- SEED SCRIPT (fixed for direct dept/title on employee, no affiliations)
-- Assumes you run psql from the project root, and CSVs are in seeds_csvs/
-- If you run elsewhere, adjust the \cd path below.

BEGIN;

\cd /Users/panayiwthzz/Desktop/IV1351/IV1351_PROJECT/seeds_csvs

-- =====================================================================
-- 1) STAGING SCHEMA (raw CSV shapes, no generated IDs here)
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS stage;

-- People
DROP TABLE IF EXISTS stage.person CASCADE;
CREATE TABLE stage.person (
  personal_number BIGINT,
  first_name      VARCHAR(500),
  last_name       VARCHAR(500),
  phone_number    VARCHAR(50),
  address         VARCHAR(500)
);
-- Employees
DROP TABLE IF EXISTS stage.employee CASCADE;
CREATE TABLE stage.employee (
  employment_id          VARCHAR(500),
  personal_number        BIGINT,
  department_name        VARCHAR(500),     
  job_title              VARCHAR(500),     
  skill_level            skill_level_t,
  salary                 NUMERIC(10,2),
  manager_employment_id  VARCHAR(500),     
  manager_name           VARCHAR(500)
);

-- Departments
DROP TABLE IF EXISTS stage.department CASCADE;
CREATE TABLE stage.department (
  department_name VARCHAR(500),
  manager         VARCHAR(500)
);

-- Job title lookup (list of distinct titles)
DROP TABLE IF EXISTS stage.job_title CASCADE;
CREATE TABLE stage.job_title (
  job_title VARCHAR(500)
);

-- Teaching activity lookup
DROP TABLE IF EXISTS stage.teaching_activity CASCADE;
CREATE TABLE stage.teaching_activity (
  activity_name VARCHAR(500),
  factor        DOUBLE PRECISION
);

-- Course layout (versioned)
DROP TABLE IF EXISTS stage.course_layout CASCADE;
CREATE TABLE stage.course_layout (
  course_code   VARCHAR(6),
  course_name   VARCHAR(500),
  min_students  INT,
  max_students  INT,
  hp            DOUBLE PRECISION,
  created_at    TIMESTAMPTZ
);

-- Course instance (bind to specific layout version or current)
DROP TABLE IF EXISTS stage.course_instance CASCADE;
CREATE TABLE stage.course_instance (
  instance_id       VARCHAR(100),
  study_year        INT,
  study_period      study_period_t,
  num_students      INT,
  course_code       VARCHAR(6),
  layout_created_at TIMESTAMPTZ,
  use_current       BOOLEAN
);

-- Planned activities per instance (use activity_name; DB will resolve IDs)
DROP TABLE IF EXISTS stage.planned_activity CASCADE;
CREATE TABLE stage.planned_activity (
  instance_id    VARCHAR(100),
  activity_name  VARCHAR(500),
  planned_hours  DOUBLE PRECISION
);

-- Allocations
DROP TABLE IF EXISTS stage.allocations CASCADE;
CREATE TABLE stage.allocations (
  instance_id    VARCHAR(100),
  activity_name  VARCHAR(500),
  employment_id  VARCHAR(500)
);

-- =====================================================================
-- 2) LOAD CSVs INTO STAGING
-- =====================================================================
\echo '==> Loading CSVs into staging...'

\copy stage.person            FROM 'person.csv'            CSV HEADER ENCODING 'UTF8'
\copy stage.employee          FROM 'employee.csv'          CSV HEADER ENCODING 'UTF8'
\copy stage.department        FROM 'department.csv'        CSV HEADER ENCODING 'UTF8'
\copy stage.job_title         FROM 'job_titles.csv'        CSV HEADER ENCODING 'UTF8'
\copy stage.teaching_activity FROM 'teaching_activity.csv' CSV HEADER ENCODING 'UTF8'
\copy stage.course_layout     FROM 'course_layout.csv'     CSV HEADER ENCODING 'UTF8'
\copy stage.course_instance   FROM 'course_instance.csv'   CSV HEADER ENCODING 'UTF8'
\copy stage.planned_activity  FROM 'planned_activity.csv'  CSV HEADER ENCODING 'UTF8'
\copy stage.allocations       FROM 'allocations.csv'       CSV HEADER ENCODING 'UTF8'

-- =====================================================================
-- 3) VALIDATION (fail fast with clear messages)
-- =====================================================================
\echo '==> Validating staging data...'

DO $$
DECLARE
  v_missing_persons int := 0;
  v_missing_mgr     int := 0;
  v_missing_dept    int := 0;
  v_missing_title   int := 0;
  v_missing_act_pa  int := 0;
  v_missing_act_al  int := 0;
BEGIN
  -- employee → person
  SELECT COUNT(*) INTO v_missing_persons
  FROM stage.employee se
  LEFT JOIN stage.person sp ON sp.personal_number = se.personal_number
  WHERE sp.personal_number IS NULL;

  IF v_missing_persons > 0 THEN
    RAISE EXCEPTION 'Seed error: % employee rows have personal_number not in person.csv', v_missing_persons;
  END IF;

  -- self-manager existence (if provided)
  SELECT COUNT(*) INTO v_missing_mgr
  FROM stage.employee se
  WHERE se.manager_employment_id IS NOT NULL
    AND se.manager_employment_id <> ''
    AND NOT EXISTS (SELECT 1 FROM stage.employee m WHERE m.employment_id = se.manager_employment_id);

  IF v_missing_mgr > 0 THEN
    RAISE EXCEPTION 'Seed error: % employee rows reference a missing manager_employment_id', v_missing_mgr;
  END IF;

  -- department_name must exist in stage.department
  SELECT COUNT(*) INTO v_missing_dept
  FROM stage.employee se
  WHERE NOT EXISTS (
    SELECT 1 FROM stage.department sd WHERE sd.department_name = se.department_name
  );

  IF v_missing_dept > 0 THEN
    RAISE EXCEPTION 'Seed error: some employees reference department_name not in department.csv';
  END IF;

  -- job_title must exist in stage.job_title
  SELECT COUNT(*) INTO v_missing_title
  FROM stage.employee se
  WHERE NOT EXISTS (
    SELECT 1 FROM stage.job_title jt WHERE jt.job_title = se.job_title
  );

  IF v_missing_title > 0 THEN
    RAISE EXCEPTION 'Seed error: some employees reference job_title not in job_titles.csv';
  END IF;

  -- activity_name must exist in stage.teaching_activity (planned_activity)
  SELECT COUNT(*) INTO v_missing_act_pa
  FROM stage.planned_activity pa
  WHERE NOT EXISTS (
    SELECT 1 FROM stage.teaching_activity ta WHERE ta.activity_name = pa.activity_name
  );

  IF v_missing_act_pa > 0 THEN
    RAISE EXCEPTION 'Seed error: planned_activity has activity_name not in teaching_activity.csv';
  END IF;

  -- activity_name must exist in stage.teaching_activity (allocations)
  SELECT COUNT(*) INTO v_missing_act_al
  FROM stage.allocations al
  WHERE NOT EXISTS (
    SELECT 1 FROM stage.teaching_activity ta WHERE ta.activity_name = al.activity_name
  );

  IF v_missing_act_al > 0 THEN
    RAISE EXCEPTION 'Seed error: allocations has activity_name not in teaching_activity.csv';
  END IF;
END$$;

-- =====================================================================
-- 4) INSERT INTO REAL TABLES (parents → children)
-- =====================================================================

\echo '==> Inserting: person'
INSERT INTO person (personal_number, first_name, last_name, phone_number, address)
SELECT DISTINCT personal_number, first_name, last_name, phone_number, address
FROM stage.person;

\echo '==> Inserting: department'
INSERT INTO department (department_name, manager)
SELECT DISTINCT department_name, manager
FROM stage.department;

\echo '==> Inserting: job_title (lookup)'
INSERT INTO job_title (job_title)
SELECT DISTINCT job_title
FROM stage.job_title;

\echo '==> Inserting: teaching_activity'
INSERT INTO teaching_activity (activity_name, factor)
SELECT DISTINCT activity_name, factor
FROM stage.teaching_activity;

\echo '==> Inserting: course_layout (versioned; trigger handles is_current)'
INSERT INTO course_layout (course_code, course_name, min_students, max_students, hp, created_at)
SELECT course_code, course_name, min_students, max_students, hp, COALESCE(created_at, now())
FROM stage.course_layout;

\echo '==> Inserting: course_instance (bind to exact version or current)'
INSERT INTO course_instance (instance_id, num_students, study_period, study_year, course_layout_id)
SELECT si.instance_id, si.num_students, si.study_period, si.study_year, cl.id
FROM stage.course_instance si
JOIN course_layout cl
  ON cl.course_code = si.course_code
 AND (
       (si.layout_created_at IS NOT NULL AND cl.created_at = si.layout_created_at)
    OR (si.layout_created_at IS NULL AND si.use_current = TRUE AND cl.is_current = TRUE)
     );

\echo '==> Inserting: planned_activity'
INSERT INTO planned_activity (instance_id, teaching_activity_id, planned_hours)
SELECT pa.instance_id, ta.id, pa.planned_hours
FROM stage.planned_activity pa
JOIN teaching_activity ta ON ta.activity_name = pa.activity_name;

\echo '==> Inserting: employee (resolve dept/title by name, person by personal_number)'
INSERT INTO employee (employment_id, person_id, skill_level, salary, manager_name,
                      employment_id_manager, department_id, job_title_id)
SELECT
  se.employment_id,
  p.id,
  se.skill_level,
  se.salary,
  NULLIF(se.manager_name,''),
  NULLIF(se.manager_employment_id,''),
  d.id,
  jt.id
FROM stage.employee se
JOIN person p       ON p.personal_number  = se.personal_number
JOIN department d   ON d.department_name  = se.department_name
JOIN job_title jt   ON jt.job_title       = se.job_title;

\echo '==> Inserting: allocations (validated against planned_activity)'
INSERT INTO allocations (instance_id, teaching_activity_id, employment_id)
SELECT al.instance_id, ta.id, al.employment_id
FROM stage.allocations al
JOIN teaching_activity ta ON ta.activity_name = al.activity_name
JOIN planned_activity  pa ON pa.instance_id = al.instance_id
                         AND pa.teaching_activity_id = ta.id;

COMMIT;

\echo '==> Seed complete.'
