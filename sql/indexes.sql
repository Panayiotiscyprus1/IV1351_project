-- Indexes to optimize query performance for the database

CREATE INDEX idx_course_instance_year_period
ON course_instance(study_year, study_period);

CREATE INDEX idx_allocations_employment_id
ON allocations(employment_id);
