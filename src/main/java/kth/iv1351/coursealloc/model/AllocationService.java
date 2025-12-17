package kth.iv1351.coursealloc.model;

import java.sql.SQLException;
import kth.iv1351.coursealloc.integration.DBHandler;

/**
 * AllocationService
 * -----------------
 * Domain-layer service for allocation-related use cases WITHOUT special constraints:
 *   - Add Exercise allocation.
 *   - Deallocate any teaching activity.
 *
 * All operations are wrapped in DBHandler.executeInTransaction(...)
 * so the DAO/integration layer controls commit/rollback.
 */
public class AllocationService {
    private final DBHandler db;

    public AllocationService(DBHandler db) {
        this.db = db;
    }

    /**
     * Use case: add/update an Exercise activity allocation.
     */
    public ExerciseAllocationInfo addExercise(String instanceId,
                                              String employmentId,
                                              double plannedHours)
            throws SQLException {

        return db.executeInTransaction(() ->
                db.addExerciseActivity(instanceId, employmentId, plannedHours)
        );
    }

    /**
     * Use case: deallocate a teaching activity for a given teacher and instance.
     * Always allowed (no special rule).
     */
    public void deallocateTeaching(String instanceId,
                                   String employmentId,
                                   String activityName)
            throws SQLException {

        db.executeInTransaction(() -> {
            long activityId = db.getTeachingActivityIdByName(activityName);
            db.deleteAllocation(instanceId, activityId, employmentId);
            return null; // Void return
        });
    }
}
