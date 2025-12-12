package kth.iv1351.coursealloc.model;

import java.sql.SQLException;
import kth.iv1351.coursealloc.integration.DBHandler;

public class CourseService {
    private final DBHandler db;

    public CourseService(DBHandler db) {
        this.db = db;
    }

    public CourseInstanceCost computeCourseCost(String instanceId)
            throws SQLException {
        return db.computeCostForInstance(instanceId);
    }

    public int increaseStudents(String instanceId, int delta) throws SQLException {
        return db.increaseNumStudents(instanceId, delta);
    }
}
