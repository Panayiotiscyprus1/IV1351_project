-- SEED SCRIPT (fixed for direct dept/title on employee, no affiliations)
-- Assumes you run psql from the project root, and CSVs are in seeds_csvs/
-- If you run elsewhere, adjust the \cd path below.

BEGIN;

\cd /Users/panayiwthzz/IV1351_project/seeds_csvs

-- 1) STAGING SCHEMA (raw CSV shapes, no generated IDs here)

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
  manager_employment_id  VARCHAR(500)  
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
  employment_id VARCHAR(500),
  job_title     VARCHAR(500)
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

-- skill
DROP TABLE IF EXISTS stage.skill CASCADE;
CREATE TABLE stage.skill (
  name VARCHAR(200)
);

-- employee_skills
DROP TABLE IF EXISTS stage.employee_skills CASCADE;
CREATE TABLE stage.employee_skills (
  employment_id VARCHAR(500),
  skill_id INT
);

-- 2) LOAD CSVs INTO STAGING

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
\copy stage.skill             FROM 'skills.csv'            CSV HEADER ENCODING 'UTF8'
\copy stage.employee_skills   FROM 'employee_skills.csv'   CSV HEADER ENCODING 'UTF8'


-- 4) INSERT INTO REAL TABLES (parents → children)

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
INSERT INTO employee (employment_id, person_id, skill_level, salary,
                      employment_id_manager, department_id, job_title_id)
SELECT
  se.employment_id,
  p.id,
  se.skill_level,
  se.salary,
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


\echo '==> Inserting: skill and employee_skills'

INSERT INTO skill (name)
SELECT DISTINCT name
FROM stage.skill
ON CONFLICT (name) DO NOTHING;

INSERT INTO employee_skills (employment_id, skill_id)
SELECT ses.employment_id, ses.skill_id
FROM stage.employee_skills ses
JOIN employee e ON e.employment_id = ses.employment_id
JOIN skill    s ON s.id = ses.skill_id
ON CONFLICT (employment_id, skill_id) DO NOTHING;

COMMIT;

\echo '==> Seed complete.'
