package kth.iv1351.coursealloc.model;

import java.sql.SQLException;

import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.integration.DBHandler.InstancePeriod;

/**
 * TeachingService
 * ---------------
 * Domain-layer service encapsulating ALL teaching-allocation business rules.
 *
 * Main rule:
 *   - A teacher may NOT teach in more than 4 course instances in the same
 *     (study_year, study_period).
 *
 * Implementation:
 *   - Uses DBHandler.executeInTransaction(...) to run the whole sequence of:
 *       * lookups
 *       * overload check
 *       * inserts/updates
 *     in a single transaction.
 *
 * Transaction handling is still done by DBHandler, not by this service.
 */
public class TeachingService {
    private final DBHandler db;

    public TeachingService(DBHandler db) {
        this.db = db;
    }

    public void allocateTeaching(String instanceId,
                                 String employmentId,
                                 String activityName,
                                 double allocatedHours)
            throws SQLException, TeacherOverloadedException {

        // We need to know after the transaction whether we violated the rule.
        final boolean[] overloaded = { false };
        final String[] overloadMessage = { null };

        db.executeInTransaction(() -> {
            // Get activity id
            long activityId = db.getTeachingActivityIdByName(activityName);

            // Get target instance year & period
            InstancePeriod ip = db.getInstancePeriod(instanceId);

            // Check if teacher already has *any* allocation on this instance
            boolean alreadyOnThisInstance =
                    db.teacherAlreadyAllocatedOnInstance(instanceId, employmentId);

            // If this is a new instance for that teacher in that period, enforce max 4 rule
            if (!alreadyOnThisInstance) {
                int currentInstances =
                        db.countTeacherInstancesInPeriod(employmentId, ip.studyYear, ip.studyPeriod);

                if (currentInstances >= 4) {
                    overloaded[0] = true;
                    overloadMessage[0] =
                            "Teacher " + employmentId + " already has " +
                            currentInstances + " course instances in period " +
                            ip.studyPeriod + " of year " + ip.studyYear +
                            " -> cannot allocate another instance.";
                    // We do NOT perform any writes in this case.
                    return null;
                }
            }

            // Rule satisfied -> perform the actual allocation
            db.upsertPlannedActivity(instanceId, activityId, allocatedHours);
            db.upsertAllocation(instanceId, activityId, employmentId, allocatedHours);

            return null;
        });

        // Outside the transaction, convert the overload condition to a domain exception
        if (overloaded[0]) {
            throw new TeacherOverloadedException(overloadMessage[0]);
        }
    }
}
