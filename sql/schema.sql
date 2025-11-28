
-- Helper ENUM types (as in the diagram)
DO $$
BEGIN
  -- study period: P1..P4
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'study_period_t') THEN
    CREATE TYPE study_period_t AS ENUM ('P1','P2','P3','P4');
  END IF;

  -- employee skill level
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'skill_level_t') THEN
    CREATE TYPE skill_level_t AS ENUM ('beginner','intermediate','advanced');
  END IF;
END$$;

-- ---------- person ----------
DROP TABLE IF EXISTS person CASCADE;
CREATE TABLE person (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,                        
  personal_number  BIGINT UNIQUE NOT NULL,                          
  first_name       VARCHAR(500),
  last_name        VARCHAR(500),                         
  address          VARCHAR(500)
);

-- ---------- phone_number ----------
DROP TABLE IF EXISTS phone_number CASCADE;
CREATE TABLE phone_number (
  id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  phone_number VARCHAR(50) NOT NULL,
  person_id    INT NOT NULL REFERENCES person(id) ON DELETE CASCADE
);


-- ---------- job_title ----------
DROP TABLE IF EXISTS job_title CASCADE;
CREATE TABLE job_title (
  id        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job_title VARCHAR(500) UNIQUE NOT NULL
);

-- department first, without FK
CREATE TABLE department (
  id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  department_name     VARCHAR(500) UNIQUE NOT NULL,
  manager_employment_id VARCHAR(500)  -- FK added later
);

-- employee, with its department FK
CREATE TABLE employee (
  employment_id  VARCHAR(500) PRIMARY KEY,
  skill_level    skill_level_t,
  person_id      INT NOT NULL UNIQUE REFERENCES person(id) ON DELETE CASCADE,
  department_id  INT NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
  job_title_id   INT NOT NULL REFERENCES job_title(id) ON DELETE RESTRICT
);

-- Since department references employee (manager), and is created before the employee table
-- we add that FK constraint afterwards
ALTER TABLE department
  ADD FOREIGN KEY (manager_employment_id)
  REFERENCES employee(employment_id)
  ON DELETE SET NULL;


-- Salary history (versioned) for each employee
DROP TABLE IF EXISTS salary CASCADE;
CREATE TABLE salary (
  id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employment_id VARCHAR(500) NOT NULL
      REFERENCES employee(employment_id) ON DELETE CASCADE,
  salary        NUMERIC(10,2) NOT NULL CHECK (salary >= 0),
  created_at    TIMESTAMP NOT NULL DEFAULT now(),
  is_current    BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------- teaching_activity ----------
DROP TABLE IF EXISTS teaching_activity CASCADE;
CREATE TABLE teaching_activity (
  id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,                           
  activity_name VARCHAR(500) UNIQUE NOT NULL,
  factor        DOUBLE PRECISION                                  
);

-- ---------- course_layout ----------
DROP TABLE IF EXISTS course_layout CASCADE;
CREATE TABLE course_layout (
  id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,                          
  course_code   VARCHAR(6) NOT NULL,                     
  course_name   VARCHAR(500),
  min_students  INT,
  max_students  INT,
  hp            DOUBLE PRECISION,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),              
  is_current    BOOLEAN NOT NULL DEFAULT TRUE                    
);

-- ---------- course_instance ----------
DROP TABLE IF EXISTS course_instance CASCADE;
CREATE TABLE course_instance (
  instance_id       VARCHAR(100) PRIMARY KEY,                    
  num_students      INT,
  study_period      study_period_t,                              -- "study_period AS ENUM('P1','P2','P3','P4')"
  study_year        INT,
  course_layout_id  INT NOT NULL
                      REFERENCES course_layout(id)
                      ON DELETE RESTRICT                        
);

-- ---------- planned_activity ----------
DROP TABLE IF EXISTS planned_activity CASCADE;
CREATE TABLE planned_activity (
  instance_id        VARCHAR(100) NOT NULL
                       REFERENCES course_instance(instance_id)
                       ON DELETE CASCADE,                        
  planned_hours      DOUBLE PRECISION,                           
  teaching_activity_id BIGINT NOT NULL
                       REFERENCES teaching_activity(id)
                       ON DELETE RESTRICT,                       
  PRIMARY KEY (instance_id, teaching_activity_id)
);

-- ---------- allocations ----------
CREATE TABLE allocations (
  instance_id          VARCHAR(100) NOT NULL,
  teaching_activity_id BIGINT      NOT NULL,
  employment_id        VARCHAR(500) NOT NULL REFERENCES employee(employment_id) ON DELETE CASCADE,
  allocated_hours DOUBLE PRECISION NOT NULL,
  FOREIGN KEY (instance_id, teaching_activity_id) REFERENCES planned_activity(instance_id, teaching_activity_id) ON DELETE CASCADE,
  -- One row per (instance, activity, teacher)
  PRIMARY KEY (instance_id, teaching_activity_id, employment_id)
);

-- ---------- skill ----------
DROP TABLE IF EXISTS skill CASCADE;
CREATE TABLE skill (
  id   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(200) UNIQUE NOT NULL
);

-- ---------- employee_skills (cross table) ----------
DROP TABLE IF EXISTS employee_skills CASCADE;
CREATE TABLE employee_skills (
  employment_id VARCHAR(500) NOT NULL REFERENCES employee(employment_id) ON DELETE CASCADE,
  skill_id INT NOT NULL REFERENCES skill(id) ON DELETE RESTRICT,
  PRIMARY KEY (employment_id, skill_id)
);
