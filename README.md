🎓 IV1351 Project — Course Layout & Teaching Load Allocations
=============================================================

This repository contains our **IV1351 (Data Storage Paradigms)** project — a complete relational model and PostgreSQL implementation for managing **Course Layouts** and **Teaching Load Allocations** at a university.

📘 Overview
-----------

The system models and enforces complex academic scheduling and allocation rules, providing:

*   ✅ **Logical and physical schema** (PostgreSQL)
    
*   🔒 **Integrity rules** via foreign keys and checks
    
*   ⚙️ **Business logic** in functions and triggers
    
*   📦 **Reproducible seeding pipeline** from CSV files
    
*   🧠 **Design notes** (course layout versioning, max-4 allocations rule, etc.)
    

🧩 What the System Manages
--------------------------

### Core Entities

EntityDescription**person**Natural persons (first/last name, phone, address, personal number)**employee**Contract-based employees linked to person (skill level, salary, manager relationship)**department** & **affiliations**Employees belong to one or more departments**teaching\_activity**Types of teaching work: Lecture, Lab, Tutorial, Seminar, Administration, Examination (each with prep-time factor)**course\_layout**Versioned definition of a course (code, name, HP, min/max students, timestamps, is\_current)**course\_instance**Concrete course delivery (year, period, students) bound to a specific layout version**planned\_activity**Per instance: which activities and how many hours (linked to teaching\_activity by ID)**allocations**Which employee is assigned to which planned activity and course instance**job\_title**One-to-one relationship for each employee’s title

🧠 Key Business Rules
---------------------

*   **Versioned Course Layouts**Each course\_code can have multiple layout rows with different created\_at.Exactly one row per course is marked is\_current = TRUE.Instances bind to a specific layout (so older instances keep historical HP values).
    
*   **Max 4 Allocations per Period**A trigger ensures an employee cannot be allocated to more than **4 distinct instances** in the same (study\_year, study\_period).
    
*   **Foreign Key Delete Behaviors**
    
    *   affiliations → **CASCADE** on employee/department delete
        
    *   planned\_activity → **CASCADE** on instance delete
        
    *   allocations → **CASCADE** when referenced planned\_activity removed
        
    *   employee.person\_id → **ON DELETE CASCADE**
        
    *   employee.employment\_id\_manager → **SET NULL** when manager deleted
        
    *   course\_instance.course\_layout\_id → **RESTRICT** (cannot delete layout in use)
        

🖥️ Tech & Prerequisites
------------------------

*   PostgreSQL **18**
    
*   macOS (Apple Silicon or Intel) / Linux
    
*   CLI access via psql
    

### macOS Tip

If using Homebrew on Apple Silicon, binaries are usually under:

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   /opt/homebrew/opt/postgresql@18/bin   `

Make sure this directory is on your PATH.

📁 Project Layout
-----------------

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   IV1351_PROJECT/  ├─ sql/  │  ├─ schema.sql        # Tables, types, constraints, indexes  │  ├─ functions.sql     # Business logic (PL/pgSQL)  │  ├─ triggers.sql      # Triggers and trigger bindings  │  └─ seeds.sql         # CSV loader + inserts  └─ seeds_csvs/     ├─ person.csv     ├─ employee.csv     ├─ department.csv     ├─ affiliations.csv     ├─ teaching_activity.csv     ├─ course_layout.csv     ├─ course_instance.csv     ├─ planned_activity.csv     ├─ allocations.csv     └─ job_titles.csv   `

🚀 Run Instructions
-------------------

You must be in the **project root directory** (IV1351\_PROJECT/) when running commands locally — relative paths in seeds.sql depend on it.

### 1️⃣ Start PostgreSQL (macOS Homebrew)

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   brew services start postgresql@18  # or restart if needed:  brew services restart postgresql@18  brew services list   `

Manual control (optional):

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   pg_ctl -D /Library/PostgreSQL/18/data -l ~/postgres.log start  pg_ctl -D /Library/PostgreSQL/18/data stop   `

### 2️⃣ Connect and Initialize Database

From Terminal:

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   psql -U postgres -h localhost   `

Inside the psql shell:

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   CREATE DATABASE iv1351;  \c iv1351  \i sql/schema.sql  \i sql/functions.sql  \i sql/triggers.sql  \i sql/seeds.sql   `

🌱 Seeding Details
------------------

The seed script will:

1.  Create a **staging schema**
    
2.  \\cd into seeds\_csvs/ (so COPY finds CSVs)
    
3.  \\copy data into staging tables
    
4.  Validate referential integrity
    
5.  Insert into the main schema in correct parent→child order
    

### CSV Notes

ItemRule**IDs in CSVs**planned\_activity.csv and allocations.csv use teaching\_activity\_id (integer IDs from teaching\_activity.csv)**Quoted fields**If a field contains commas (like addresses), quote it with "**Encoding**UTF8 (safe for å / ä / ö)**Paths**No need to edit paths — relative paths handled automatically

Verify teaching activity IDs:

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   SELECT id, activity_name FROM teaching_activity ORDER BY id;   `

🧮 Quick Verification Queries
-----------------------------

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   -- People / Employees  SELECT COUNT(*) FROM person;  SELECT COUNT(*) FROM employee;  SELECT e.employment_id, p.first_name, p.last_name  FROM employee e JOIN person p ON p.id = e.person_id  ORDER BY p.last_name;  -- Teaching activities and IDs  SELECT * FROM teaching_activity ORDER BY id;  -- Course layouts & instances  SELECT course_code, course_name, hp, is_current, created_at  FROM course_layout  ORDER BY course_code, created_at;  SELECT instance_id, study_year, study_period, course_layout_id  FROM course_instance  ORDER BY instance_id;  -- Planned activities & allocations  SELECT * FROM planned_activity ORDER BY instance_id, teaching_activity_id;  SELECT * FROM allocations ORDER BY instance_id, teaching_activity_id, employment_id;   `

🔄 How to Reset the Database
----------------------------

Plain textANTLR4BashCC#CSSCoffeeScriptCMakeDartDjangoDockerEJSErlangGitGoGraphQLGroovyHTMLJavaJavaScriptJSONJSXKotlinLaTeXLessLuaMakefileMarkdownMATLABMarkupObjective-CPerlPHPPowerShell.propertiesProtocol BuffersPythonRRubySass (Sass)Sass (Scss)SchemeSQLShellSwiftSVGTSXTypeScriptWebAssemblyYAMLXML`   \c postgres  DROP DATABASE IF EXISTS iv1351;  CREATE DATABASE iv1351;  \c iv1351  \i sql/schema.sql  \i sql/functions.sql  \i sql/triggers.sql  \i sql/seeds.sql   `

⚠️ Common Pitfalls & Fixes
--------------------------

ErrorLikely CauseFixmissing data for column ...Blank/malformed line in CSVRemove empty lines in CSVallocations/planned\_activity inserted 0 rowsInvalid IDs or missing linksEnsure IDs match existing entries in teaching\_activity and planned\_activityduplicate key on course\_codeOld UNIQUE constraint remainsDrop old UNIQUE(course\_code) and keep only (course\_code, created\_at) + partial unique on is\_current

💡 Design Highlights
--------------------

### Versioned Course Layouts

*   Every update creates a **new row** with the same course\_code and new created\_at
    
*   Exactly one row per course is marked as current (is\_current = TRUE)
    
*   Historical queries remain valid for past course instances
    

### Allocation Constraint

*   Trigger enforces a max of **4 allocations per employee** in the same study period
    

### Safe Delete Rules

*   Cascades prevent orphan records while preserving integrity for referenced layouts
    

🧾 License & Contributions
--------------------------

Use freely for **academic purposes**.Pull Requests (PRs) welcome for improvements such as:

*   Synthetic data generators
    
*   Test suites
    
*   Example analytical queries
    

🧑‍💻 **Authors:** IV1351 Team📚 **Course:** Data Storage Paradigms (KTH Royal Institute of Technology)📅 **Year:** 2025
