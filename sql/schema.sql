
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
  phone_number     VARCHAR(50),                           
  address          VARCHAR(500)
);

-- ---------- department ----------
DROP TABLE IF EXISTS department CASCADE;
CREATE TABLE department (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,                     
  department_name  VARCHAR(500) UNIQUE NOT NULL,
  manager          VARCHAR(500)                                 
);

-- ---------- job_title ----------
DROP TABLE IF EXISTS job_title CASCADE;
CREATE TABLE job_title (
  id        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job_title VARCHAR(500) UNIQUE NOT NULL
);

-- ---------- employee ----------
DROP TABLE IF EXISTS employee CASCADE;
CREATE TABLE employee (
  employment_id          VARCHAR(500) UNIQUE NOT NULL PRIMARY KEY,   
  skill_level            skill_level_t,                               
  salary                 NUMERIC(10,2),
  manager_name           VARCHAR(500),
  employment_id_manager  VARCHAR(500) REFERENCES employee(employment_id) ON DELETE SET NULL,
  person_id              INT NOT NULL UNIQUE REFERENCES person(id) ON DELETE CASCADE,
  department_id          INT NOT NULL REFERENCES department(id) ON DELETE RESTRICT,
  job_title_id           INT NOT NULL REFERENCES job_title(id) ON DELETE RESTRICT
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
  FOREIGN KEY (instance_id, teaching_activity_id) REFERENCES planned_activity(instance_id, teaching_activity_id) ON DELETE CASCADE,
  -- One row per (instance, activity, teacher)
  PRIMARY KEY (instance_id, teaching_activity_id, employment_id)
);
