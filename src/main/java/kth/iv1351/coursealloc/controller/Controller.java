package kth.iv1351.coursealloc.controller;


import java.sql.SQLException;

import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.integration.DBHandler.InstancePeriod;
import kth.iv1351.coursealloc.model.CourseInstanceCost;
import kth.iv1351.coursealloc.model.ExerciseAllocationInfo;
import kth.iv1351.coursealloc.model.TeacherOverloadedException;

/**
 * Temporary placeholder controller for testing startup and wiring.
 *
 * At this stage it does nothing except prove that StartUp → Controller → View works.
 * Later you’ll add real use-cases (compute cost, increase students, etc.).
 */
public class Controller {
    private final DBHandler db;

    public Controller(DBHandler db) {
        this.db = db;
    }

    /**
     * Use-case: compute teaching cost for a course instance in a given year.
     *
     * Handles transaction boundaries:
     *  - beginTransaction()
     *  - commit() on success
     *  - rollback() on SQLException
     */
    public CourseInstanceCost computeCourseCost(String instanceId)
        throws SQLException {
    try {
        db.beginTransaction(); // even for reads, to show proper transaction handling
        CourseInstanceCost cost = db.computeCostForInstance(instanceId);
        db.commit();
        return cost;
    } catch (SQLException e) {
        db.rollback();
        throw e;
    }
    }

    /**
 * Increases number of students for an instance by the given delta and returns
 * the new num_students value after the update.
 *
 * Transaction flow:
 *   begin
 *     -> increaseNumStudents(instanceId, delta)
 *   commit on success, rollback on SQLException.
 */
    public int increaseStudents(String instanceId, int delta) throws SQLException {
        try {
            db.beginTransaction();
            int newNum = db.increaseNumStudents(instanceId, delta);
            db.commit();
            return newNum;
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }


    public ExerciseAllocationInfo addExercise(String instanceId,
                                              String employmentId,
                                              double plannedHours)
            throws SQLException {
        try {
            db.beginTransaction();
            ExerciseAllocationInfo info =
                    db.addExerciseActivity(instanceId, employmentId, plannedHours);
            db.commit();
            return info;
        } catch (SQLException e) {
            db.rollback();
            throw e;
        }
    }


    /**
 * Use-case: allocate a teaching activity (e.g. Lecture, Tutorial, Exercise)
 * for a teacher on a course instance, enforcing:
 *
 *   A teacher is not allowed to teach in more than 4 course instances
 *   in the same period (same study_year + study_period).
 *
 * Business logic lives here (controller), DAO only does data access.
 */
public void allocateTeaching(String instanceId,
                             String employmentId,
                             String activityName,
                             double allocatedHours)
        throws SQLException, TeacherOverloadedException {
    try {
        db.beginTransaction();

        // 1. Get activity id (CRUD)
        long activityId = db.getTeachingActivityIdByName(activityName);

        // 2. Get target instance year & period (CRUD)
        InstancePeriod ip = db.getInstancePeriod(instanceId);

        // 3. Check if teacher already has *any* allocation on this instance
        boolean alreadyOnThisInstance =
                db.teacherAlreadyAllocatedOnInstance(instanceId, employmentId);

        // 4. If this is a new instance for that teacher in that period,
        //    enforce the "max 4 instances per period" rule
        if (!alreadyOnThisInstance) {
            int currentInstances =
                    db.countTeacherInstancesInPeriod(
                            employmentId, ip.studyYear, ip.studyPeriod);

            if (currentInstances >= 4) {
                db.rollback();
                throw new TeacherOverloadedException(
                    "Teacher " + employmentId + " already has " +
                    currentInstances + " course instances in period " +
                    ip.studyPeriod + " of year " + ip.studyYear +
                    " -> cannot allocate another instance."
                );
            }
        }

        // 5. All good -> do the actual allocation (CRUD)
        db.upsertPlannedActivity(instanceId, activityId, allocatedHours);
        db.upsertAllocation(instanceId, activityId, employmentId, allocatedHours);

        db.commit();
    } catch (TeacherOverloadedException e) {
        // we already rolled back above but do it again defensively
        db.rollback();
        throw e;
    } catch (SQLException e) {
        db.rollback();
        throw e;
    }
}

    /**
     * Use-case: deallocate a teaching activity for a teacher on an instance.
     * No special rule here; it's always allowed.
     */
    public void deallocateTeaching(String instanceId,
        String employmentId,
        String activityName)
    throws SQLException {
    try {
    db.beginTransaction();

    long activityId = db.getTeachingActivityIdByName(activityName);
    db.deleteAllocation(instanceId, activityId, employmentId);

    db.commit();
    } catch (SQLException e) {
    db.rollback();
    throw e;
    }
    }


    
}
