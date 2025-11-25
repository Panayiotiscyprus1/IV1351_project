-- All OLAP (task2) queries

-- All four queries revolve around “hours * factor” for activities and teachers, so we make
-- one reusable view (as suggested in tips for seminar 2) to have everything in place,
-- and our OLAP queries retrieve data from there.

\echo 'Creating helper view'
-- View: per-allocation, factor-adjusted hours, enriched with course & teacher info
CREATE OR REPLACE VIEW v_allocation_hours AS
SELECT ci.instance_id, cl.course_code, cl.hp, ci.study_year, ci.study_period, ci.num_students,
    pa.teaching_activity_id, ta.activity_name, ta.factor, pa.planned_hours, a.employment_id,
    p.first_name || ' ' || p.last_name AS teacher_name,
    pa.planned_hours * ta.factor AS allocated_hours
FROM course_instance      ci
JOIN course_layout        cl ON cl.id = ci.course_layout_id
JOIN planned_activity     pa ON pa.instance_id = ci.instance_id
JOIN teaching_activity    ta ON ta.id = pa.teaching_activity_id
LEFT JOIN allocations     a  ON a.instance_id = pa.instance_id
                             AND a.teaching_activity_id = pa.teaching_activity_id
LEFT JOIN employee        e  ON e.employment_id = a.employment_id
LEFT JOIN person          p  ON p.id = e.person_id;


\echo 'running query 1'
DROP MATERIALIZED VIEW IF EXISTS "query1";
CREATE MATERIALIZED VIEW "query1" AS
SELECT course_code AS "Course Code", instance_id AS "Course Instance ID", hp AS "HP", study_period AS "Period", num_students AS "Students",
    SUM(CASE WHEN activity_name = 'Lecture' THEN planned_hours ELSE 0 END) AS lecture_hours,
    SUM(CASE WHEN activity_name = 'Tutorial' THEN planned_hours ELSE 0 END) AS tutorial_hours,
    SUM(CASE WHEN activity_name = 'Lab' THEN planned_hours ELSE 0 END) AS lab_hours,
    SUM(CASE WHEN activity_name = 'Seminar' THEN planned_hours ELSE 0 END) AS seminar_hours,
    SUM(CASE WHEN activity_name = 'Others' THEN planned_hours ELSE 0 END) AS other_overhead_hours,
    ROUND((2*hp + 28 + 0.2*num_students)::numeric, 2) AS Admin,
    ROUND((32 + 0.725*num_students)::numeric, 2) AS Exam,
    (SUM(CASE WHEN activity_name = 'Lecture' THEN planned_hours ELSE 0 END)
    + SUM(CASE WHEN activity_name = 'Tutorial' THEN planned_hours ELSE 0 END)
    + SUM(CASE WHEN activity_name = 'Lab' THEN planned_hours ELSE 0 END)
    + SUM(CASE WHEN activity_name = 'Seminar' THEN planned_hours ELSE 0 END)
    + SUM(CASE WHEN activity_name = 'Others' THEN planned_hours ELSE 0 END)
    + ROUND((2*hp + 28 + 0.2*num_students)::numeric, 2)
    + ROUND((32 + 0.725*num_students)::numeric, 2)
    ) AS total_hours
FROM v_allocation_hours WHERE study_year = 2025
GROUP BY course_code, instance_id, hp, study_period, num_students ORDER BY course_code, instance_id;

SELECT * FROM "query1";


\echo 'running query 2'
SELECT b.course_code AS "Course Code", b.instance_id AS "Course Instance ID", b.hp AS "HP",
  b.study_period AS "Period", b.teacher_name AS "Teacher Name", jt.job_title AS "Designation",
  ROUND(b.lecture_hours::numeric,2) AS "Lecture Hours",
  ROUND(b.tutorial_hours::numeric,2) AS "Tutorial Hours",
  ROUND(b.lab_hours::numeric,2) AS "Lab Hours",
  ROUND(b.seminar_hours::numeric,2) AS "Seminar Hours",
  ROUND(b.other_overhead_hours::numeric,2) AS "Other Overhead Hours",
  ROUND((2*b.hp + 28 + 0.2*b.num_students)::numeric, 2) AS "Admin",
  ROUND((32 + 0.725*b.num_students)::numeric,2) AS "Exam",
  ROUND((b.lecture_hours + b.tutorial_hours + b.lab_hours + b.seminar_hours + b.other_overhead_hours 
  + (2*b.hp + 28 + 0.2*b.num_students)+ (32 + 0.725*b.num_students))::numeric, 2) AS "Total Hours"
FROM (
  SELECT v.course_code, v.instance_id, v.hp, v.study_period, v.teacher_name, v.employment_id, MAX(v.num_students) AS num_students,
    SUM(CASE WHEN v.activity_name = 'Lecture'  THEN v.allocated_hours ELSE 0 END) AS lecture_hours,
    SUM(CASE WHEN v.activity_name = 'Tutorial' THEN v.allocated_hours ELSE 0 END) AS tutorial_hours,
    SUM(CASE WHEN v.activity_name = 'Lab'      THEN v.allocated_hours ELSE 0 END) AS lab_hours,
    SUM(CASE WHEN v.activity_name = 'Seminar'  THEN v.allocated_hours ELSE 0 END) AS seminar_hours,
    SUM(CASE WHEN v.activity_name = 'Others'   THEN v.allocated_hours ELSE 0 END) AS other_overhead_hours
  FROM v_allocation_hours v
  WHERE v.study_year = 2025 AND v.instance_id = '2025-50273'   -- pick the course instance you want
  AND v.employment_id IS NOT NULL -- only allocated teachers
  GROUP BY v.course_code, v.instance_id, v.hp, v.study_period, v.teacher_name, v.employment_id
) b
JOIN employee  e  ON e.employment_id = b.employment_id
JOIN job_title jt ON jt.id           = e.job_title_id
ORDER BY b.teacher_name, b.course_code, b.instance_id;


\echo 'running query 3'
DROP MATERIALIZED VIEW IF EXISTS "query3";
CREATE MATERIALIZED VIEW "query3" AS
SELECT b.course_code AS "Course Code", b.instance_id AS "Course Instance ID", b.hp AS "HP", b.study_period AS "Period", b.teacher_name AS "Teacher Name",
  ROUND(b.lecture_hours::numeric,2) AS "Lecture Hours", ROUND(b.tutorial_hours::numeric,2) AS "Tutorial Hours", 
  ROUND(b.lab_hours::numeric,2) AS "Lab Hours", ROUND(b.seminar_hours::numeric,2) AS "Seminar Hours", 
  ROUND(b.other_overhead_hours::numeric,2) AS "Other Overhead Hours",
  ROUND((2*b.hp + 28 + 0.2*b.num_students)::numeric,2) AS "Admin", ROUND((32 + 0.725*b.num_students)::numeric,2) AS "Exam",
  (
    b.lecture_hours + b.tutorial_hours + b.lab_hours + b.seminar_hours + b.other_overhead_hours
    + (2*b.hp + 28 + 0.2*b.num_students)
    + (32 + 0.725*b.num_students)
  ) AS "Total Hours"
FROM (
  SELECT course_code, instance_id, hp, study_period, teacher_name,
    MAX(num_students) AS num_students,
    SUM(CASE WHEN activity_name = 'Lecture'  THEN allocated_hours ELSE 0 END) AS lecture_hours,
    SUM(CASE WHEN activity_name = 'Tutorial' THEN allocated_hours ELSE 0 END) AS tutorial_hours,
    SUM(CASE WHEN activity_name = 'Lab'      THEN allocated_hours ELSE 0 END) AS lab_hours,
    SUM(CASE WHEN activity_name = 'Seminar'  THEN allocated_hours ELSE 0 END) AS seminar_hours,
    SUM(CASE WHEN activity_name = 'Others'   THEN allocated_hours ELSE 0 END) AS other_overhead_hours
  FROM v_allocation_hours v
  WHERE study_year = 2025
  GROUP BY course_code, instance_id, hp, study_period, teacher_name
) b
ORDER BY b.teacher_name, b.course_code, b.instance_id;

SELECT * FROM "query3";


\echo 'running query 4'
SELECT employment_id AS "Employment ID", teacher_name AS "Teacher's Name", study_period AS period, COUNT(DISTINCT instance_id) AS no_of_courses
FROM v_allocation_hours
WHERE study_year = 2025 AND study_period = 'P2' -- current period here
GROUP BY employment_id, teacher_name, study_period
HAVING COUNT(DISTINCT instance_id) > 0  -- this is our threshold N
ORDER BY no_of_courses DESC, teacher_name;