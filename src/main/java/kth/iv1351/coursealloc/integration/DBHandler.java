package kth.iv1351.coursealloc.integration;

import kth.iv1351.coursealloc.model.CourseInstanceCost;

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
        "SELECT SUM(vah.allocated_hours * s.salary) AS total_cost " +
        "FROM v_allocation_hours vah " +                
        "JOIN course_instance ci ON ci.instance_id = vah.instance_id " +
        "JOIN salary s ON s.employment_id = vah.employment_id " +
        "WHERE vah.instance_id = ? " +
        "  AND ci.study_year = EXTRACT(YEAR FROM CURRENT_DATE)::INT " +
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

}

