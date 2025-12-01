package kth.iv1351.coursealloc.controller;


import java.sql.SQLException;

import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.model.CourseInstanceCost;

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

}
