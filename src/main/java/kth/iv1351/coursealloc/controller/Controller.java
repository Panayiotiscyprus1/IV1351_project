package kth.iv1351.coursealloc.controller;

import java.sql.SQLException;

import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.model.CourseInstanceCost;
import kth.iv1351.coursealloc.model.ExerciseAllocationInfo;
import kth.iv1351.coursealloc.model.TeacherOverloadedException;

// NEW domain services
import kth.iv1351.coursealloc.model.CourseService;
import kth.iv1351.coursealloc.model.AllocationService;
import kth.iv1351.coursealloc.model.TeachingService;

/**
 * Controller
 * Acts ONLY as a transaction manager + use-case coordinator.
 * ALL business rules have been moved to the model/domain layer
 * (CourseService, AllocationService, TeachingService).
 * Responsibilities:
 *   -> Start/commit/rollback transactions.
 *   ->  Delegate domain logic to the service classes.
 * No business logic remains here.
 */
public class Controller {
    private final DBHandler db;

    // Domain services
    private final CourseService courseService;
    private final AllocationService allocationService;
    private final TeachingService teachingService;

    public Controller(DBHandler db) {
        this.db = db;
        this.courseService = new CourseService(db);
        this.allocationService = new AllocationService(db);
        this.teachingService = new TeachingService(db);
    }

    public CourseInstanceCost computeCourseCost(String instanceId)
            throws SQLException {
        try {
            db.beginTransaction();
            CourseInstanceCost cost = courseService.computeCourseCost(instanceId);
            db.commit();
            return cost;
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
    }

    public int increaseStudents(String instanceId, int delta)
            throws SQLException {
        try {
            db.beginTransaction();
            int newCount = courseService.increaseStudents(instanceId, delta);
            db.commit();
            return newCount;
        } catch (Exception e) {
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
            ExerciseAllocationInfo info = allocationService.addExercise(instanceId, employmentId, plannedHours);
            db.commit();
            return info;
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
    }

    public void allocateTeaching(String instanceId,
                                 String employmentId,
                                 String activityName,
                                 double allocatedHours)
            throws SQLException, TeacherOverloadedException {
        try {
            db.beginTransaction();
            teachingService.allocateTeaching(instanceId, employmentId, activityName, allocatedHours);
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
    }

    public void deallocateTeaching(String instanceId,
                                   String employmentId,
                                   String activityName)
            throws SQLException {
        try {
            db.beginTransaction();
            allocationService.deallocateTeaching(instanceId, employmentId, activityName);
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
    }
}
