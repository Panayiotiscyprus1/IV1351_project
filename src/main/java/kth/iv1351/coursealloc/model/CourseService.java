package kth.iv1351.coursealloc.model;

import java.sql.SQLException;
import kth.iv1351.coursealloc.integration.DBHandler;

/**
 * CourseService
 * -------------
 * Domain-layer service that encapsulates all course-related use cases:
 *   - Compute cost for a course instance.
 *   - Increase number of students.
 *
 * It uses DBHandler's executeInTransaction(...) so that the integration layer
 * owns transaction handling, while this class owns business meaning.
 */
public class CourseService {
    private final DBHandler db;

    public CourseService(DBHandler db) {
        this.db = db;
    }

    /**
     * Use case: compute the teaching cost for one course instance.
     * Read-only, but still wrapped in a transaction to demonstrate proper layering.
     */
    public CourseInstanceCost computeCourseCost(String instanceId) throws SQLException {
        return db.executeInTransaction(() ->
                db.computeCostForInstance(instanceId)
        );
    }

    /**
     * Use case: increase num_students by the given delta.
     * Needs a read–modify–write, so we wrap it inside one transaction.
     */
    public int increaseStudents(String instanceId, int delta) throws SQLException {
        return db.executeInTransaction(() ->
                db.increaseNumStudents(instanceId, delta)
        );
    }
}
