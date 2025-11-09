🎓 IV1351 Project 
=================

📘 Overview
-----------

The system models and enforces academic scheduling and allocation rules, providing:

*   **Logical and physical schema** (PostgreSQL)
    
*   **Integrity rules** via foreign keys and checks
    
*   **Business logic** in functions and triggers
    
*   **Reproducible seeding pipeline** from CSV files
    

What the System Manages
--------------------------

### Core Entities

**person** : Natural persons (first/last name, phone, address, personal number)
**employee** : Contract-based employees linked to person (skill level, salary, manager relationship)
**department** & **affiliations** : Employees belong to one or more departments 
**teaching\_activity** : Types of teaching work: Lecture, Lab, Tutorial, Seminar, Administration, Examination (each with prep-time factor)**course\_layout** : Versioned definition of a course (code, name, HP, min/max students, timestamps, is\_current)
**course\_instance** : Concrete course delivery (year, period, students) bound to a specific layout version
**planned\_activity** : Per instance: which activities and how many hours (linked to teaching\_activity by ID)
**allocations** : Which employee is assigned to which planned activity and course instance
**job\_title** : One-to-one relationship for each employee’s title

Key Business Rules
------------------

*   **Versioned Course Layouts** : Each course\_code can have multiple layout rows with different created\_at.Exactly one row per course is marked is\_current = TRUE. Instances bind to a specific layout (so older instances keep historical HP values).
    
*   **Max 4 Allocations per Period** : A trigger ensures an employee cannot be allocated to more than **4 distinct instances** in the same (study\_year, study\_period).
        

Tech & Prerequisites
------------------------

*   PostgreSQL **18**
    
*   CLI access via psql
    

Project Layout
-----------------

IV1351_PROJECT/  
├─ sql/  
│  ├─ schema.sql        # Tables, types, constraints, indexes  
│  ├─ functions.sql     # Business logic (Procedural Language/PostgreSQL)  
│  ├─ triggers.sql      # Triggers
│  └─ seeds.sql         # CSV loader + inserts  
└─ seeds_csvs/     
    ├─ person.csv     
    ├─ employee.csv     
    ├─ department.csv     
    ├─ affiliations.csv     
    ├─ teaching_activity.csv     
    ├─ course_layout.csv     
    ├─ course_instance.csv     
    ├─ planned_activity.csv     
    ├─ allocations.csv     
    └─ job_titles.csv   

Run Instructions
----------------

You must be in the **project root directory** (IV1351\_PROJECT/) when running commands locally — relative paths in seeds.sql depend on it.

1. `psql -U postgres -h localhost` 
2. `CREATE DATABASE iv1351;`
3. `\c iv1351`
4. `\i sql/schema.sql`  
5. `\i sql/functions.sql`  
6. `\i sql/triggers.sql`  
7. `\i sql/seeds.sql`

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

-- Planned activities & allocations -- 
SELECT * FROM planned_activity ORDER BY instance_id, teaching_activity_id; 
SELECT * FROM allocations ORDER BY instance_id, teaching_activity_id, employment_id;
```

**Authors:** Panayiotis Charalambous, Milana Timonina, Alex Ambersky
**Course:** Data Storage Paradigms (KTH Royal Institute of Technology) 
**Year:** 2025
