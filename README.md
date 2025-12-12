IV1351 Project 
===============

Overview
---------

The system models and enforces academic scheduling and allocation rules, providing:

*   **Logical and physical schema** (PostgreSQL)
    
*   **Integrity rules** via foreign keys and checks
    
*   **Business logic** in functions and triggers
    
*   **Reproducible seeding pipeline** from CSV files

*   **OLAP**  that returns 4 basic tables used to generate analysis report and
creates materialized views to optimize retrievals of frequently used queries

* **Optimization** via indexes and materialized views
    
* **Programmatic Access** via Java simple program using CLI interface. 

What the System Manages
--------------------------

### Core Entities

**person** : Natural persons (first/last name, address, personal number)

**phone\_number** : Phone number or phone numbers of each person, since one per-
son can have 0 or multiple phone numbers.

**employee** : Contract-based employees linked to person (skill level, salary, manager relationship)

**salary** : Salary or salaries (store historical salaries)per employee. Only 1 salary is
marked as current per employee.

**department**: Employees belong to exactly one department 

**teaching\_activity** : Types of teaching work: Lecture, Lab, Tutorial, Seminar, Administration, Examination (each with prep-time factor)

**course\_layout** : Versioned definition of a course (code, name, HP, min/max students, timestamps, is\_current)

**course\_instance** : Concrete course delivery (year, period, students) bound to a specific layout version

**planned\_activity** : Per instance: which activities and how many hours (linked to teaching\_activity by ID)

**allocations** : Which employee is assigned to which planned activity and course instance and for how many hours.

**job\_title** : One-to-one relationship for each employee’s title

**skill** : unique skills that an employee can have

**employee\_skills** : which employee has which skills (cross table to handle the many
to many relation)

Key Business Rules
------------------

*   **Versioned Course Layouts** : Each course\_code can have multiple layout rows with different created\_at. Exactly one row per course is marked is\_current = TRUE. Instances bind to a specific layout (so older instances keep historical HP values).

* **Versioning of Salary** : Each employee can have multiple lsalary rows with different created\_at. Exactly one row per salary is marked is\_current = TRUE. (so older salaries are kept in the Database).
    
*   **Max 4 Allocations per Period** : A trigger ensures an employee cannot be allocated to more than **4 distinct instances** in the same (study\_year, study\_period).
        

Tech & Prerequisites
------------------------

*   PostgreSQL **18**
    
*   CLI access via psql

*   Maven from programmatic access and JDBC
    

Project Layout
-----------------

IV1351_PROJECT/  

* sql/ : sql scripts for creating the database, function and triggers, populating the database with example data, creating indexes and views 

* seeds_csvs/ : csv file used for seeding

* erd/ : Logical model with physical properties of the DB

* Analysis/ : EXPLAIN ANALYZE posgresql output on various queries (from OLAP) used to optimize the DB.

* .vscode/ : json file that adds src/main/java to the java project path.

* bash_scripts/ : 2 bash scripts the user adapts to his configurations and runs to create the DB and start the program

* src/main/java/kth/iv1351/coursealloc/ : all java code following MVC layered architecture for programmatic access to the DB. 


Run Instructions
----------------

You must be in the **project root directory** (IV1351\_PROJECT/) when running commands locally — relative paths in seeds.sql depend on it.

* Configure the 2 bash scripts to your user credentials and update the paths
* In StartUP.java update url user and password to your Postgres credentials
* Run the 2 bash scripts, in 2 terminal windows

Seeding Details
---------------

The seed script will:
1.  Create a **staging schema** - A staging schema lets you load raw CSVs into temporary tables, clean/validate/transform them, and resolve natural keys 
                                to DB IDs before touching your real tables. This makes seeding safer and atomic—if anything fails, you roll back without polluting the production schema
2.  \\cd into seeds\_csvs/ (so COPY finds CSVs)
3.  \\copy data into staging tables
4.  Validate referential integrity
5.  Insert into the main schema in correct parent→child order


Quick Verification Queries
-----------------------------

```sql
-- Count all persons
SELECT COUNT(*) FROM person;

-- Count all employees
SELECT COUNT(*) FROM employee;

-- List employees with their names
SELECT e.employment_id, p.first_name, p.last_name FROM employee e
JOIN person p ON p.id = e.person_id ORDER BY p.last_name;

-- Teaching activities and ids -- 
SELECT * FROM teaching_activity ORDER BY id;

-- Course layouts & instances --
SELECT course_code, course_name, hp, is_current, created_at FROM course_layout ORDER BY course_code, created_at; 

SELECT instance_id, study_year, study_period, course_layout_id FROM course_instance ORDER BY instance_id;

SELECT instance_id, study_year, study_period, course_layout_id, course_code, is_current
FROM course_instance JOIN course_layout ON course_instance.course_layout_id = course_layout.id ORDER BY instance_id;

-- Planned activities & allocations -- 
SELECT * FROM planned_activity ORDER BY instance_id, teaching_activity_id; 
SELECT * FROM allocations ORDER BY instance_id, teaching_activity_id, employment_id;

SELECT instance_id, planned_hours, activity_name, factor FROM planned_activity JOIN 
teaching_activity ON teaching_activity.id = planned_activity.teaching_activity_id ORDER BY instance_id, teaching_activity_id;

-- Spot check employees resolved to dept & title
SELECT e.employment_id, p.first_name, p.last_name, d.department_name, jt.job_title FROM employee e
JOIN person p   ON p.id = e.person_id JOIN department d ON d.id = e.department_id JOIN job_title jt ON jt.id = e.job_title_id
ORDER BY e.employment_id;

-- Planned activities per instance
SELECT ci.instance_id, ta.activity_name, pa.planned_hours FROM planned_activity pa JOIN teaching_activity ta 
ON ta.id = pa.teaching_activity_id JOIN course_instance ci   ON ci.instance_id = pa.instance_id
ORDER BY ci.instance_id, ta.activity_name;

-- Allocations are consistent with planned_activity
SELECT a.instance_id, ta.activity_name, a.employment_id FROM allocations a
JOIN teaching_activity ta ON ta.id = a.teaching_activity_id ORDER BY a.instance_id, ta.activity_name, a.employment_id;

-- Skills per employment_id
SELECT e.employment_id, s.name AS skill_name FROM employee_skills es JOIN employee e 
ON e.employment_id = es.employment_id JOIN skill s    ON s.id = es.skill_id ORDER BY e.employment_id, s.name;

-- employee and there manager
SELECT e.employment_id, p.first_name  AS employee_first_name, p.last_name   AS employee_last_name,
  m.employment_id AS manager_employment_id, mp.first_name AS manager_first_name, mp.last_name  AS manager_last_name
FROM employee e JOIN person   p  ON p.id = e.person_id LEFT JOIN employee m  ON m.employment_id = e.employment_id_manager
LEFT JOIN person   mp ON mp.id = m.person_id ORDER BY p.last_name, p.first_name;

-- employees and there managers
SELECT e.employment_id AS employee_id, pe.first_name || ' ' || pe.last_name AS employee_name,
  d.department_name, m.employment_id AS manager_id, pm.first_name || ' ' || pm.last_name AS manager_name
FROM employee e JOIN department d ON e.department_id = d.id
LEFT JOIN employee m ON d.manager_employment_id = m.employment_id
LEFT JOIN person pe ON pe.id = e.person_id
LEFT JOIN person pm ON pm.id = m.person_id
ORDER BY d.department_name, employee_name;

```

FOR VERIFYING AGAINST CLI
--------------------------
```sql

-- increment students
SELECT num_students FROM course_instance WHERE instance_id = '2025-52000';

-- add exercise, alloc, dealloc
SELECT instance_id, activity_name, employment_id, allocated_hours FROM allocations
JOIN teaching_activity ON teaching_activity.id = allocations.teaching_activity_id
WHERE instance_id = '2025-52000';

```

**Authors:** Panayiotis Charalambous, Milana Timonina, Alex Ambersky
**Course:** Data Storage Paradigms (KTH Royal Institute of Technology) 
**Year:** 2025
