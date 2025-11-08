
-- SEED SCRIPT 


--\echo '==> Configure CSV directory (absolute path to your .csv files)'
-- CHANGE THIS PATH:
--\set datadir '/Users/panayiwthzz/Desktop/IV1351/IV1351_project'

BEGIN;
\cd /Users/panayiwthzz/Desktop/IV1351/IV1351_PROJECT/seeds_csvs

-- ---------------------------------------------------------------------
-- 0) Safety: ensure person.personal_number is BIGINT (for 12-digit values)
--    (If it's already BIGINT, this block does nothing.)
-- ---------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_name = 'person'
        AND column_name = 'personal_number'
        AND data_type <> 'bigint'
  ) THEN
    EXECUTE 'ALTER TABLE person ALTER COLUMN personal_number TYPE BIGINT USING personal_number::bigint';
  END IF;
END$$;

-- =====================================================================
-- 1) STAGING SCHEMA
--    (Exact shapes of your CSV files; no generated IDs here.)
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS stage;

-- People
DROP TABLE IF EXISTS stage.person CASCADE;
CREATE TABLE stage.person (
  personal_number BIGINT,               -- 12-digit yyyymmddxxxx without leading zeros
  first_name      VARCHAR(500),
  last_name       VARCHAR(500),
  phone_number    VARCHAR(50),
  address         VARCHAR(500)
);

-- Employees (user-supplied employment_id, links to person via personal_number)
DROP TABLE IF EXISTS stage.employee CASCADE;
CREATE TABLE stage.employee (
  employment_id          INT,          -- contract number (your PK in employee)
  personal_number        BIGINT,       -- FK resolver to person
  skill_level            skill_level_t,
  salary                 NUMERIC(10,2),
  manager_employment_id  INT,          -- self-FK by contract number (nullable for top)
  manager_name           VARCHAR(500)
);

-- Departments
DROP TABLE IF EXISTS stage.department CASCADE;
CREATE TABLE stage.department (
  department_name VARCHAR(500),
  manager         VARCHAR(500)
);

-- Affiliations (employee ↔ department)
DROP TABLE IF EXISTS stage.affiliations CASCADE;
CREATE TABLE stage.affiliations (
  employment_id   INT,
  department_name VARCHAR(500)
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

-- Course instance (binds to a specific layout version or the current one)
DROP TABLE IF EXISTS stage.course_instance CASCADE;
CREATE TABLE stage.course_instance (
  instance_id       VARCHAR(100),
  study_year        INT,
  study_period      study_period_t,
  num_students      INT,
  course_code       VARCHAR(6),
  layout_created_at TIMESTAMPTZ,       -- exact version (optional)
  use_current       BOOLEAN            -- OR bind to current
);

-- Planned activities per instance
DROP TABLE IF EXISTS stage.planned_activity CASCADE;
CREATE TABLE stage.planned_activity (
  instance_id          VARCHAR(100),
  teaching_activity_id INT,
  planned_hours        DOUBLE PRECISION
);

-- Allocations (teacher ↔ planned activity on instance)
DROP TABLE IF EXISTS stage.allocations CASCADE;
CREATE TABLE stage.allocations (
  instance_id          VARCHAR(100),
  teaching_activity_id INT,
  employment_id        INT
);

-- Job titles
DROP TABLE IF EXISTS stage.job_title CASCADE;
CREATE TABLE stage.job_title (
  employment_id INT,
  job_title     VARCHAR(500)
);

-- =====================================================================
-- 2) LOAD CSVs INTO STAGING
--    (\copy reads from your local filesystem; replace the path above)
-- =====================================================================
\echo '==> Loading CSVs into staging...'

\copy stage.person            FROM 'person.csv'            CSV HEADER ENCODING 'UTF8'
\copy stage.employee          FROM 'employee.csv'          CSV HEADER ENCODING 'UTF8'
\copy stage.department        FROM 'department.csv'        CSV HEADER ENCODING 'UTF8'
\copy stage.affiliations      FROM 'affiliations.csv'      CSV HEADER ENCODING 'UTF8'
\copy stage.teaching_activity FROM 'teaching_activity.csv' CSV HEADER ENCODING 'UTF8'
\copy stage.course_layout     FROM 'course_layout.csv'     CSV HEADER ENCODING 'UTF8'
\copy stage.course_instance   FROM 'course_instance.csv'   CSV HEADER ENCODING 'UTF8'
\copy stage.planned_activity  FROM 'planned_activity.csv'  CSV HEADER ENCODING 'UTF8'
\copy stage.allocations       FROM 'allocations.csv'       CSV HEADER ENCODING 'UTF8'
\copy stage.job_title         FROM 'job_titles.csv'        CSV HEADER ENCODING 'UTF8'

-- =====================================================================
-- 3) STRICT VALIDATION (fail fast if obvious mismatches)
-- =====================================================================
\echo '==> Validating staging data...'

DO $$
DECLARE
  v_missing_persons int := 0;
  v_missing_mgr     int := 0;
BEGIN
  -- A) Ensure every stage.employee.personal_number exists in stage.person
  SELECT COUNT(*) INTO v_missing_persons
  FROM stage.employee se
  LEFT JOIN stage.person sp ON sp.personal_number = se.personal_number
  WHERE sp.personal_number IS NULL;

  IF v_missing_persons > 0 THEN
    RAISE EXCEPTION 'Seed error: % employee rows have personal_number not present in person.csv', v_missing_persons;
  END IF;

  -- B) Ensure each manager_employment_id (if given) exists in stage.employee
  SELECT COUNT(*) INTO v_missing_mgr
  FROM stage.employee se
  WHERE se.manager_employment_id IS NOT NULL
    AND NOT EXISTS (
      SELECT 1 FROM stage.employee m WHERE m.employment_id = se.manager_employment_id
    );

  IF v_missing_mgr > 0 THEN
    RAISE EXCEPTION 'Seed error: % employee rows reference a missing manager_employment_id', v_missing_mgr;
  END IF;
END$$;

-- =====================================================================
-- 4) INSERT INTO REAL TABLES (parents -> children)
--    - DB generates: person.id, department.id, teaching_activity.id, course_layout.id
--    - You provide:  employee.employment_id (INT contract number)
-- =====================================================================

\echo '==> Inserting: person'
INSERT INTO person (personal_number, first_name, last_name, phone_number, address)
SELECT DISTINCT personal_number, first_name, last_name, phone_number, address
FROM stage.person;

\echo '==> Inserting: employee (uses user-provided employment_id)'
INSERT INTO employee (employment_id, person_id, skill_level, salary, manager_name, employment_id_manager)
SELECT
  se.employment_id,                    -- provided contract number
  p.id,                                -- generated person.id
  se.skill_level,
  se.salary,
  NULLIF(se.manager_name,''),
  se.manager_employment_id             -- self-FK by employment_id (nullable)
FROM stage.employee se
JOIN person p ON p.personal_number = se.personal_number;

\echo '==> Inserting: department'
INSERT INTO department (department_name, manager)
SELECT DISTINCT department_name, manager
FROM stage.department;

\echo '==> Inserting: affiliations (employee -> department)'
INSERT INTO affiliations (employment_id, department_id)
SELECT sa.employment_id, d.id
FROM stage.affiliations sa
JOIN department d ON d.department_name = sa.department_name;

\echo '==> Inserting: teaching_activity'
INSERT INTO teaching_activity (activity_name, factor)
SELECT DISTINCT activity_name, factor
FROM stage.teaching_activity;

\echo '==> Inserting: course_layout (trigger maintains is_current per course_code)'
INSERT INTO course_layout (course_code, course_name, min_students, max_students, hp, created_at)
SELECT course_code, course_name, min_students, max_students, hp, COALESCE(created_at, now())
FROM stage.course_layout;

\echo '==> Inserting: course_instance (bind to specific version OR current)'
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
JOIN teaching_activity ta ON ta.id = pa.teaching_activity_id;

\echo '==> Inserting: allocations (validated against planned_activity)'
INSERT INTO allocations (instance_id, teaching_activity_id, employment_id)
SELECT al.instance_id,
       al.teaching_activity_id,
       al.employment_id
FROM stage.allocations al
JOIN planned_activity pa
  ON pa.instance_id = al.instance_id
 AND pa.teaching_activity_id = al.teaching_activity_id;

\echo '==> Inserting: job_title'
INSERT INTO job_title (job_title, employment_id)
SELECT jt.job_title, jt.employment_id
FROM stage.job_title jt;

COMMIT;



\echo '==> Seed complete.'
