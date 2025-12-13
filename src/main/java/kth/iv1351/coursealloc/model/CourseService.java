package kth.iv1351.coursealloc.model;

import java.sql.SQLException;
import kth.iv1351.coursealloc.integration.DBHandler;

/**
 * CourseService
 * ----------------------------
 * Domain-layer service that contains ALL business logic related to courses.
 * Responsibilities:
 *   -> Compute the cost of a course instance.
 *   -> Increase the number of enrolled students in an instance.
 * This class isolates course-related business rules from the controller.
 * No SQL appears here — DBHandler (DAO) handles database access.
 */

public class CourseService {
    private final DBHandler db;

    public CourseService(DBHandler db) {
        this.db = db;
    }

    public CourseInstanceCost computeCourseCost(String instanceId)
            throws SQLException {
        return db.computeCostForInstance(instanceId);
    }

    public int increaseStudents(String instanceId, int delta)
            throws SQLException {
        return db.increaseNumStudents(instanceId, delta);
    }
}

