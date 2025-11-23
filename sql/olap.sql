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


\echo 'running query 3'
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