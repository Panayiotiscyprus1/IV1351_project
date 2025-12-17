package kth.iv1351.coursealloc.controller;

import java.sql.SQLException;

import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.model.CourseInstanceCost;
import kth.iv1351.coursealloc.model.ExerciseAllocationInfo;
import kth.iv1351.coursealloc.model.TeacherOverloadedException;

import kth.iv1351.coursealloc.model.CourseService;
import kth.iv1351.coursealloc.model.AllocationService;
import kth.iv1351.coursealloc.model.TeachingService;

/**
 * Controller
 * ----------
 * Application façade.
 *
 * Responsibilities:
 *   - Expose use-case methods to the UI / CLI.
 *   - Delegate each use case to the appropriate domain service.
 *   - Perform NO business logic.
 *   - Perform NO transaction management (no begin/commit/rollback).
 *
 * All business rules live in the model/domain layer (services).
 * All transaction handling is performed by DBHandler via executeInTransaction(...).
 */
public class Controller {

    private final CourseService courseService;
    private final AllocationService allocationService;
    private final TeachingService teachingService;

    public Controller(DBHandler db) {
        this.courseService = new CourseService(db);
        this.allocationService = new AllocationService(db);
        this.teachingService = new TeachingService(db);
    }

    public CourseInstanceCost computeCourseCost(String instanceId)
            throws SQLException {
        return courseService.computeCourseCost(instanceId);
    }

    public int increaseStudents(String instanceId, int delta)
            throws SQLException {
        return courseService.increaseStudents(instanceId, delta);
    }

    public ExerciseAllocationInfo addExercise(String instanceId,
                                              String employmentId,
                                              double plannedHours)
            throws SQLException {
        return allocationService.addExercise(instanceId, employmentId, plannedHours);
    }

    public void allocateTeaching(String instanceId,
                                 String employmentId,
                                 String activityName,
                                 double allocatedHours)
            throws SQLException, TeacherOverloadedException {
        teachingService.allocateTeaching(instanceId, employmentId, activityName, allocatedHours);
    }

    public void deallocateTeaching(String instanceId,
                                   String employmentId,
                                   String activityName)
            throws SQLException {
        allocationService.deallocateTeaching(instanceId, employmentId, activityName);
    }
}
