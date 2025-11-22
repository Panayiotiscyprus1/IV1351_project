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