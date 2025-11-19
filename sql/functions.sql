-- FUNCTIONS used by triggers (matches notes on the diagram)

-- A) When inserting a new course_layout for an existing course_code,
--    flip the previous "current" version to FALSE.
CREATE OR REPLACE FUNCTION trg_set_current_layout()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE course_layout
     SET is_current = FALSE
   WHERE course_code = NEW.course_code
     AND is_current  = TRUE;
  NEW.is_current := TRUE;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- B) Enforce: A TEACHER MUST NOT BE ALLOCATED MORE THAN FOUR DIFFERENT INSTANCES
--    SIMULTANEOUSLY DURING A PARTICULAR PERIOD (same year + period).

--  - allocations rows are inserted per teacher *and* (instance, activity).
--  - The limit ("4") is per teacher, per (study_year, study_period),
--    and we count DISTINCT instances (so multiple activities in the same instance
--    do not inflate the count).

CREATE OR REPLACE FUNCTION trg_check_max4_allocations()
RETURNS TRIGGER AS $$
DECLARE
  v_year   INT;             -- the year of the instance we are trying to assign
  v_period study_period_t;  -- the period (P1..P4) of that instance
  v_count  INT;             -- how many distinct instances the teacher already has in that (year, period)
BEGIN

  -- 1) Resolve the (year, period) of the target instance from the NEW row.
  --    NEW.instance_id is being inserted; we look it up in course_instance.

  SELECT ci.study_year, ci.study_period
    INTO v_year, v_period
  FROM course_instance ci
  WHERE ci.instance_id = NEW.instance_id;

  -- Defensive check: if someone passed a non-existent instance_id, stop early.
  IF v_year IS NULL THEN
    RAISE EXCEPTION
      'allocations.instance_id % does not reference a valid course_instance',
      NEW.instance_id;
  END IF;


  -- 2) Count how many DISTINCT instances this teacher is already allocated to
  --    within the SAME (study_year, study_period).
  --
  --    We use COUNT(DISTINCT a.instance_id) so that if the teacher has multiple
  --    allocations for different activities within the same instance, that
  --    instance is still counted only ONCE toward the "max 4" rule.
  
  SELECT COUNT(DISTINCT a.instance_id)
    INTO v_count
  FROM allocations a
  JOIN course_instance ci2 ON ci2.instance_id = a.instance_id
  WHERE a.employment_id = NEW.employment_id
    AND ci2.study_year   = v_year
    AND ci2.study_period = v_period;

  -- 3) If the count is already 4 or more, reject the insert with an error.

  IF v_count >= 4 THEN
    RAISE EXCEPTION
      'Employee % already has 4 course instances in % %',
      NEW.employment_id, v_period, v_year;
  END IF;

  -- 4) Otherwise accept the row (the insert proceeds).
 
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
