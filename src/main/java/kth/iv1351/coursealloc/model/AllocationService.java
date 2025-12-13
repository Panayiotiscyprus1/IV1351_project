package kth.iv1351.coursealloc.model;

import java.sql.SQLException;
import kth.iv1351.coursealloc.integration.DBHandler;

/**
 * AllocationService
 * ----------------------------
 * Domain-layer service responsible for allocation actions that do NOT enforce 
 * special business constraints, such as Exercises and deletions.
 *
 * Responsibilities:
 *   • Add an exercise-type planned activity to a course instance.
 *   • Deallocate an activity from a teacher.
 *
 * No business rules here — only straightforward domain operations.
 */
public class AllocationService {
    private final DBHandler db;

    public AllocationService(DBHandler db) {
        this.db = db;
    }

    public ExerciseAllocationInfo addExercise(String instanceId,
                                              String employmentId,
                                              double plannedHours)
            throws SQLException {
        return db.addExerciseActivity(instanceId, employmentId, plannedHours);
    }

    public void deallocateTeaching(String instanceId,
                                   String employmentId,
                                   String activityName)
            throws SQLException {

        long activityId = db.getTeachingActivityIdByName(activityName);
        db.deleteAllocation(instanceId, activityId, employmentId);
    }
}
