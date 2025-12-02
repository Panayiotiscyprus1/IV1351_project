package kth.iv1351.coursealloc.model;

/**
 * DTO for the result "Course Code, Instance, Period, Planned Cost, Actual Cost".
 */

public class CourseInstanceCost {
    private final String courseCode;
    private final String instanceId;
    private final String period;
    private final double plannedCostKsek;
    private final double actualCostKsek;

    public CourseInstanceCost(String courseCode, String instanceId, String period,
                              double plannedCostKsek, double actualCostKsek) {
        this.courseCode = courseCode;
        this.instanceId = instanceId;
        this.period = period;
        this.plannedCostKsek = plannedCostKsek;
        this.actualCostKsek = actualCostKsek;
    }

    // Getters
    public String getCourseCode() { return courseCode; }
    public String getInstanceId() { return instanceId; }
    public String getPeriod() { return period; }
    public double getPlannedCostKsek() { return plannedCostKsek; }
    public double getActualCostKsek() { return actualCostKsek; }
}
