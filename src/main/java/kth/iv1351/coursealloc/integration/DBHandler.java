package kth.iv1351.coursealloc.integration;

import kth.iv1351.coursealloc.model.CourseInstanceCost;
import kth.iv1351.coursealloc.model.ExerciseAllocationInfo;

import java.sql.*;

/**
 * DBHandler (DAO / Integration Layer)
 * -----------------------------------
 * Responsibilities:
 *   - Owns a single JDBC Connection (auto-commit disabled).
 *   - Provides CRUD and query methods for the rest of the application.
 *   - Provides a generic transaction wrapper (executeInTransaction) that
 *     begins, commits and rolls back transactions.
 *
 * IMPORTANT:
 *   - No business rules here: all domain rules are in the model/domain layer.
 *   - Controllers do NOT call begin/commit/rollback directly anymore.
 *   - Instead, domain services call executeInTransaction(...) when they
 *     need a multi-statement operation to be atomic.
 */
public class DBHandler {
    private final Connection connection;

    public DBHandler(String url, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(url, user, password);
        this.connection.setAutoCommit(false); // manual transaction control
    }

    /**
     * Functional interface used by the transaction wrapper.
     * A lambda passed to executeInTransaction(...) must implement this and
     * can return a value of type T or throw SQLException.
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute() throws SQLException;
    }

    /**
     * Transaction wrapper.
     * - Begins a transaction.
     * - Executes the callback.
     * - Commits on success.
     * - Rolls back on ANY SQLException.
     * This is the ONLY place where commit/rollback is done.
     */
    public <T> T executeInTransaction(TransactionCallback<T> action) throws SQLException {
        try {
            beginTransaction();
            T result = action.execute();
            commit();
            return result;
        } catch (SQLException e) {
            rollback();
            throw e;
        }
    }

    /**
     * Starts a transaction.
     * Private: only used by executeInTransaction().
     */
    private void beginTransaction() throws SQLException {
        // With autoCommit=false, we're already in a transaction by default,
        // but this method is kept for semantic clarity and future extension.
    }

    // Commits the current transaction. Private: all callers go through executeInTransaction().
    private void commit() throws SQLException {
        connection.commit();
    }

    // Rolls back the current transaction. Private: all callers go through executeInTransaction().
    private void rollback() throws SQLException {
        connection.rollback();
    }

    /**
     * Simple connectivity test.
     */
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
     * Computes the planned and actual teaching cost for a given course instance in the current year.
     * NOTE: This method does NOT contain transaction code itself.
     * Domain services can choose to call it inside executeInTransaction(...) if
     * they want a consistent snapshot. For this use case, it is read-only.
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
     * Private to DBHandler and only used inside cost computation.
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

    // Computes the average hourly salary across all current salary rows.
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

    // Planned part of the cost:
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

    // Actual part of the cost:
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

    // ============================================================================
    //  STUDENT COUNT UPDATE (READ–MODIFY–WRITE)
    // ============================================================================

    /**
     * Increases num_students for a course instance by delta.
     * NOTE: This method does NOT open/commit the transaction itself.
     * It is meant to be called inside executeInTransaction(...) from a domain service.
     */
    public int increaseNumStudents(String instanceId, int delta) throws SQLException {
        // Lock the row and read current num_students
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

        // Write back the new value
        String updateSql =
                "UPDATE course_instance " +
                "SET num_students = ? " +
                "WHERE instance_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
            ps.setInt(1, newValue);
            ps.setString(2, instanceId);
            ps.executeUpdate();
        }

        // Return the updated num_students
        return newValue;
    }

    // ============================================================================
    //  EXERCISE ACTIVITY
    // ============================================================================

    /**
     * Adds or updates an Exercise activity and allocation in one logical operation.
     * - Ensures a teaching_activity 'Exercise' exists (getOrCreateExerciseActivityId).
     * - Upserts planned_activity.
     * - Upserts allocations.
     * - Reads back a summary row from v_allocation_hours.
     *
     * This method itself does NOT commit or roll back; the caller wraps it
     * inside executeInTransaction(...) if needed.
     */
    public ExerciseAllocationInfo addExerciseActivity(String instanceId,
                                                      String employmentId,
                                                      double plannedHours)
            throws SQLException {
        long exerciseActivityId = getOrCreateExerciseActivityId();

        // planned_activity: planned_hours
        upsertPlannedExercise(instanceId, exerciseActivityId, plannedHours);

        // allocations: allocated_hours (reuse plannedHours as initial load)
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
     * If it exists, updates planned_hours; otherwise inserts a new row.
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
     * Inserts or updates a row in allocations for (instance, Exercise activity, teacher).
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
                    throw new SQLException(
                            "No Exercise allocation found in v_allocation_hours for instance "
                                    + instanceId + " and teacher " + employmentId
                    );
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

    // ============================================================================
    //  GENERIC LOOKUPS / HELPERS FOR DOMAIN SERVICES
    // ============================================================================

    /** Looks up a teaching_activity.id by its name. */
    public long getTeachingActivityIdByName(String activityName) throws SQLException {
        String sql = "SELECT id FROM teaching_activity WHERE activity_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, activityName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Unknown teaching activity: " + activityName);
                }
                return rs.getLong("id");
            }
        }
    }

    /** Simple DTO for study_year and study_period of an instance. */
    public static class InstancePeriod {
        public final int studyYear;
        public final String studyPeriod;

        public InstancePeriod(int studyYear, String studyPeriod) {
            this.studyYear = studyYear;
            this.studyPeriod = studyPeriod;
        }
    }

    /** Reads study_year and study_period from course_instance for the given instance_id. */
    public InstancePeriod getInstancePeriod(String instanceId) throws SQLException {
        String sql =
                "SELECT study_year, study_period " +
                "FROM course_instance " +
                "WHERE instance_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Course instance not found: " + instanceId);
                }
                int year = rs.getInt("study_year");
                String period = rs.getString("study_period");
                return new InstancePeriod(year, period);
            }
        }
    }

    /**
     * Returns how many distinct course instances this teacher has allocations in
     * for a given study_year, study_period.
     */
    public int countTeacherInstancesInPeriod(String employmentId,
                                             int studyYear,
                                             String studyPeriod) throws SQLException {
        String sql =
                "SELECT COUNT(DISTINCT a.instance_id) AS cnt " +
                "FROM allocations a " +
                "JOIN course_instance ci ON ci.instance_id = a.instance_id " +
                "WHERE a.employment_id = ? " +
                "  AND ci.study_year   = ? " +
                "  AND ci.study_period::text = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, employmentId);
            ps.setInt(2, studyYear);
            ps.setString(3, studyPeriod);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("cnt");
            }
        }
    }

    /**
     * Returns true if this teacher has at least one allocation on this instance (any activity).
     */
    public boolean teacherAlreadyAllocatedOnInstance(String instanceId,
                                                     String employmentId) throws SQLException {
        String sql =
                "SELECT 1 " +
                "FROM allocations " +
                "WHERE instance_id = ? AND employment_id = ? " +
                "LIMIT 1";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setString(2, employmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Upserts a planned_activity row for any activity (not just Exercise). */
    public void upsertPlannedActivity(String instanceId,
                                      long teachingActivityId,
                                      double plannedHours) throws SQLException {
        String sql =
                "INSERT INTO planned_activity (instance_id, teaching_activity_id, planned_hours) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (instance_id, teaching_activity_id) " +
                "DO UPDATE SET planned_hours = EXCLUDED.planned_hours";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setLong(2, teachingActivityId);
            ps.setDouble(3, plannedHours);
            ps.executeUpdate();
        }
    }

    /** Pure CRUD: insert or update an allocation row with given hours. */
    public void upsertAllocation(String instanceId,
                                 long teachingActivityId,
                                 String employmentId,
                                 double allocatedHours) throws SQLException {
        String sql =
                "INSERT INTO allocations (instance_id, teaching_activity_id, employment_id, allocated_hours) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (instance_id, teaching_activity_id, employment_id) " +
                "DO UPDATE SET allocated_hours = EXCLUDED.allocated_hours";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setLong(2, teachingActivityId);
            ps.setString(3, employmentId);
            ps.setDouble(4, allocatedHours);
            ps.executeUpdate();
        }
    }

    /** Pure CRUD: delete an allocation row. */
    public void deleteAllocation(String instanceId,
                                 long teachingActivityId,
                                 String employmentId) throws SQLException {
        String sql =
                "DELETE FROM allocations " +
                "WHERE instance_id = ? " +
                "  AND teaching_activity_id = ? " +
                "  AND employment_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setLong(2, teachingActivityId);
            ps.setString(3, employmentId);
            ps.executeUpdate();
        }
    }
}
