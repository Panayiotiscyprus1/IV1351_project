package kth.iv1351.coursealloc.model;

import java.sql.SQLException;

import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.integration.DBHandler.InstancePeriod;

/**
 * TeachingService
 * ----------------------------
 * Domain-layer service encapsulating ALL teaching allocation business rules.
 * Responsibilities:
 *   -> Enforce the rule: a teacher may teach in MAX 4 course instances per (study_year, study_period).
 *   -> Perform teaching allocations after validation.
 * This class is the ONLY place where allocation constraints are enforced.
 */
public class TeachingService {
    private final DBHandler db;

    public TeachingService(DBHandler db) {
        this.db = db;
    }

    public void allocateTeaching(String instanceId, String employmentId, String activityName, double allocatedHours) throws SQLException, TeacherOverloadedException {

        long activityId = db.getTeachingActivityIdByName(activityName);

        // Get study year & period of the instance
        InstancePeriod ip = db.getInstancePeriod(instanceId);

        // Determine if teacher already teaches on this instance
        boolean alreadyAllocated = db.teacherAlreadyAllocatedOnInstance(instanceId, employmentId);

        // Enforce "max 4 instances per teacher per period" business rule
        if (!alreadyAllocated) {
            int currentCount = db.countTeacherInstancesInPeriod(employmentId, ip.studyYear, ip.studyPeriod);

            if (currentCount >= 4) {
                throw new TeacherOverloadedException(
                    "Teacher " + employmentId + " already teaches "
                    + currentCount + " course instances in period "
                    + ip.studyPeriod + " of " + ip.studyYear + "."
                );
            }
        }

        // If rule satisfied -> perform actual allocation
        db.upsertPlannedActivity(instanceId, activityId, allocatedHours);
        db.upsertAllocation(instanceId, activityId, employmentId, allocatedHours);
    }
}

