package kth.iv1351.coursealloc.integration;

import kth.iv1351.coursealloc.model.CourseInstanceCost;
import kth.iv1351.coursealloc.model.ExerciseAllocationInfo;
import java.sql.*;

/**
 * Handles all DB access. Already has:
 *   - Connection field
 *   - constructor(url, user, password)
 *   - testConnection()
 *   - beginTransaction/commit/rollback
 */
public class DBHandler {
    private final Connection connection;

    public DBHandler(String url, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(url, user, password);
        this.connection.setAutoCommit(false);
    }

    public void beginTransaction() throws SQLException { }
    public void commit() throws SQLException { connection.commit(); }
    public void rollback() throws SQLException { connection.rollback(); }

    public void testConnection() throws SQLException {
        String sql = "SELECT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                System.out.println("DB test OK, SELECT 1 returned: " + rs.getInt(1));
            }
        }
    }

  
// ============================================================================
//  COST CALCULATION USING EXISTING OLAP VIEWS (CURRENT YEAR)
// ============================================================================

/**
 * Computes the planned and actual teaching cost for a given course instance
 * in the CURRENT year.
 *
 * It uses:
 *   - A PLANNED OLAP helper view to get total planned hours for this instance.
 *   - v_allocation_hours (or your allocation OLAP helper) to get allocated hours
 *     per teacher and multiplies by that teacher's hourly salary.
 *
 * Returns a CourseInstanceCost DTO containing:
 *   courseCode, instanceId, studyPeriod, plannedCostKsek, actualCostKsek.
 *
 * IMPORTANT:
 *   - You MUST adjust the view and column names in fetchPlannedPart() and
 *     fetchActualPart() to match your schema.
 *   - All filtering is done for the current year using:
 *       study_year = EXTRACT(YEAR FROM CURRENT_DATE)::INT
 */
public CourseInstanceCost computeCostForInstance(String instanceId) throws SQLException {
    // 1. Planned part: total planned hours * average hourly salary
    PlannedAggregate planned = fetchPlannedPart(instanceId);

    // 2. Actual part: SUM(allocated_hours * hourly_salary) for all teachers
    double actualCostKsek = fetchActualPart(instanceId);

    // 3. Build the DTO used by Controller/View
    return new CourseInstanceCost(
            planned.courseCode,
            instanceId,
            planned.studyPeriod,
            planned.plannedCostKsek,
            actualCostKsek
    );
}

/**
 * Small helper struct to keep the planned aggregation result together.
 * It's private to DBHandler and only used inside cost computation.
 */
private static class PlannedAggregate {
    final String courseCode;
    final String studyPeriod;
    final double plannedCostKsek;

    PlannedAggregate(String courseCode, String studyPeriod, double plannedCostKsek) {
        this.courseCode = courseCode;
        this.studyPeriod = studyPeriod;
        this.plannedCostKsek = plannedCostKsek;
    }
}

/**
 * Computes the average hourly salary across all (current) salary rows.
 *
 * Assumes you have a table 'salary' with a column 'hourly_salary'.
 * If you don't have an 'is_current' flag, you can remove that WHERE condition.
 */
private double fetchAverageHourlySalary() throws SQLException {
    String sql =
        "SELECT AVG(salary) AS avg_hourly " +
        "FROM salary " +
        "WHERE is_current = TRUE";  

    try (PreparedStatement ps = connection.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        if (!rs.next() || rs.getObject("avg_hourly") == null) {
            throw new SQLException("Could not compute average hourly salary.");
        }
        return rs.getDouble("avg_hourly");
    }
}

/**
 * Planned part of the cost:
 *
 *  1. Uses your PLANNED OLAP helper view to get:
 *       - course_code
 *       - study_period
 *       - total planned hours for this instance in the current year.
 *  2. Multiplies total planned hours by the average hourly salary.
 *  3. Converts SEK -> kSEK by dividing by 1000.
 *
 * You MUST replace:
 *   - 'planned_olap_view' with the name of your real planned-hours OLAP helper view.
 *   - 'planned_hours' with the correct column (or expression) that gives planned hours.
 */
private PlannedAggregate fetchPlannedPart(String instanceId) throws SQLException {
    double avgHourlySalary = fetchAverageHourlySalary();

    String sql =
        "SELECT h.course_code, h.study_period, " +
        "       SUM(h.planned_hours) AS total_planned_hours " +  
        "FROM v_allocation_hours h " +                            
        "JOIN course_instance ci ON ci.instance_id = h.instance_id " +
        "WHERE h.instance_id = ? " +
        "  AND ci.study_year = EXTRACT(YEAR FROM CURRENT_DATE)::INT " +
        "GROUP BY h.course_code, h.study_period";

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, instanceId);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("No planned hours for instance " + instanceId);
            }

            String courseCode = rs.getString("course_code");
            String period     = rs.getString("study_period");
            double totalHours = rs.getDouble("total_planned_hours");

            double plannedCostSek  = totalHours * avgHourlySalary;
            double plannedCostKsek = plannedCostSek / 1000.0;

            return new PlannedAggregate(courseCode, period, plannedCostKsek);
        }
    }
}

/**
 * Actual part of the cost:
 *
 *  1. Uses your allocation OLAP view (v_allocation_hours or similar) which
 *     contains allocated_hours per (instance, teacher/employment_id).
 *  2. Joins that view with salary on employment_id.
 *  3. Sums allocated_hours * hourly_salary for this instance in the current year.
 *  4. Converts SEK -> kSEK by dividing by 1000.
 *
 * You MUST ensure:
 *   - 'v_allocation_hours' (or your helper view) has:
 *        instance_id, allocated_hours, employment_id
 *   - 'salary' has: employment_id, hourly_salary (and optionally is_current)
 */
private double fetchActualPart(String instanceId) throws SQLException {
    String sql =
        "SELECT SUM(q.\"Total Hours\" * s.salary) AS total_cost " +
        "FROM \"query2\" q " +
        "JOIN salary s ON s.employment_id = q.\"Employment ID\" " +
        "WHERE q.\"Course Instance ID\" = ? " +
        "  AND s.is_current = TRUE";               

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, instanceId);

        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next() || rs.getObject("total_cost") == null) {
                // No allocations or no salary data => actual cost = 0
                return 0.0;
            }
            double totalCostSek = rs.getDouble("total_cost");
            return totalCostSek / 1000.0;
        }
    }
}



/**
 * Increases the number of registered students for a course instance by the
 * given delta (e.g. +100).
 *
 * Uses SELECT ... FOR UPDATE to lock the row while reading the current
 * num_students, then writes back the new value.
 *
 * IMPORTANT: This method DOES NOT commit or rollback.
 * The controller is responsible for transaction boundaries.
 */
public int increaseNumStudents(String instanceId, int delta) throws SQLException {
    // 1. Lock the row and read current num_students
    String selectSql =
        "SELECT num_students " +
        "FROM course_instance " +
        "WHERE instance_id = ? " +
        "FOR UPDATE";

    int current;
    try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
        ps.setString(1, instanceId);
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Course instance not found: " + instanceId);
            }
            current = rs.getInt("num_students");
        }
    }

    int newValue = current + delta;

    // 2. Write back the new value
    String updateSql =
        "UPDATE course_instance " +
        "SET num_students = ? " +
        "WHERE instance_id = ?";

    try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
        ps.setInt(1, newValue);
        ps.setString(2, instanceId);
        ps.executeUpdate();
    }

    // 3. Return the updated num_students
    return newValue;
}






    /**
     * Adds a new teaching activity called "Exercise" for a given course instance,
     * ensures it exists in teaching_activity, adds a planned_activity row for that
     * instance, and allocates the specified teacher to it.
     *
     * Returns a small DTO (ExerciseAllocationInfo) describing the affected
     * course instance and teacher for this "Exercise" activity.
     *
     * IMPORTANT:
     *   - This method does NOT handle transactions (no commit/rollback).
     *     The Controller must wrap it in begin/commit/rollback.
     *
     * @param instanceId   The course instance ID, e.g. "2025-50273".
     * @param employmentId The teacher's employment_id (as stored in allocations).
     * @param plannedHours Planned hours for this Exercise activity on this instance.
     */
    public ExerciseAllocationInfo addExerciseActivity(String instanceId,
        String employmentId,
        double plannedHours)
        throws SQLException {
        long exerciseActivityId = getOrCreateExerciseActivityId();

        // planned_activity: planned_hours
        upsertPlannedExercise(instanceId, exerciseActivityId, plannedHours);

        // allocations: allocated_hours (we reuse plannedHours as initial allocated load)
        insertExerciseAllocation(instanceId, exerciseActivityId, employmentId, plannedHours);

        return fetchExerciseAllocationInfo(instanceId, employmentId);
        }


    /**
     * Finds the id of teaching_activity with activity_name = 'Exercise'.
     * If it does not exist, inserts it and returns the new id.
     */
    private long getOrCreateExerciseActivityId() throws SQLException {
        // Try to find existing activity
        String selectSql =
            "SELECT id FROM teaching_activity WHERE activity_name = 'Exercise'";

        try (PreparedStatement ps = connection.prepareStatement(selectSql);
            ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        // Not found -> insert new
        String insertSql =
            "INSERT INTO teaching_activity (activity_name) " +
            "VALUES ('Exercise') " +
            "RETURNING id";

        try (PreparedStatement ps = connection.prepareStatement(insertSql);
            ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Failed to insert Exercise activity.");
            }
            return rs.getLong("id");
        }
    }

    /**
     * Ensures there is a planned_activity row for this instance + Exercise.
     * If it exists, updates planned_hours; otherwise, inserts a new row.
     *
     * Assumes UNIQUE/PK on (instance_id, teaching_activity_id).
     */
    private void upsertPlannedExercise(String instanceId,
        long exerciseActivityId,
        double plannedHours) throws SQLException {
    String sql =
    "INSERT INTO planned_activity (instance_id, teaching_activity_id, planned_hours) " +
    "VALUES (?, ?, ?) " +
    "ON CONFLICT (instance_id, teaching_activity_id) " +
    "DO UPDATE SET planned_hours = EXCLUDED.planned_hours";

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setString(1, instanceId);
    ps.setLong(2, exerciseActivityId);
    ps.setDouble(3, plannedHours);
    ps.executeUpdate();
    }
    }


    /**
     * Inserts a row in allocations for (instance, Exercise activity, teacher).
     * If it already exists, does nothing.
     */
    private void insertExerciseAllocation(String instanceId,
        long exerciseActivityId,
        String employmentId,
        double allocatedHours) throws SQLException {
        String sql =
        "INSERT INTO allocations (instance_id, teaching_activity_id, employment_id, allocated_hours) " +
        "VALUES (?, ?, ?, ?) " +
        "ON CONFLICT (instance_id, teaching_activity_id, employment_id) " +
        "DO UPDATE SET allocated_hours = EXCLUDED.allocated_hours";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, instanceId);
        ps.setLong(2, exerciseActivityId);
        ps.setString(3, employmentId);
        ps.setDouble(4, allocatedHours);
        ps.executeUpdate();
        }
        }


    /**
     * Reads v_allocation_hours to get a summary row describing the Exercise
     * allocation we just created, for the given instance and teacher.
     *
     * Uses v_allocation_hours columns:
     *   course_code, instance_id, study_period, activity_name, teacher_name, employment_id
     */
    private ExerciseAllocationInfo fetchExerciseAllocationInfo(String instanceId,
        String employmentId)
    throws SQLException {
    String sql =
    "SELECT course_code, instance_id, study_period, activity_name, teacher_name " +
    "FROM v_allocation_hours " +
    "WHERE instance_id = ? " +
    "  AND employment_id = ? " +
    "  AND activity_name = 'Exercise' " +
    "ORDER BY course_code, teacher_name " +
    "LIMIT 1";

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setString(1, instanceId);
    ps.setString(2, employmentId);

    try (ResultSet rs = ps.executeQuery()) {
    if (!rs.next()) {
    throw new SQLException("No Exercise allocation found in v_allocation_hours for instance "
    + instanceId + " and teacher " + employmentId);
    }

    String courseCode   = rs.getString("course_code");
    String period       = rs.getString("study_period");
    String activityName = rs.getString("activity_name");
    String teacherName  = rs.getString("teacher_name");

    return new ExerciseAllocationInfo(
    courseCode,
    instanceId,
    period,
    activityName,
    teacherName
    );
    }
    }
    }


}

