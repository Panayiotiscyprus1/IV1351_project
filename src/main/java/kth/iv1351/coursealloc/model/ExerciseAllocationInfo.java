package kth.iv1351.coursealloc.model;

public class ExerciseAllocationInfo {
    private final String courseCode;
    private final String instanceId;
    private final String period;
    private final String activityName;
    private final String teacherName;

    public ExerciseAllocationInfo(String courseCode, String instanceId,
                                  String period, String activityName, String teacherName) {
        this.courseCode = courseCode;
        this.instanceId = instanceId;
        this.period = period;
        this.activityName = activityName;
        this.teacherName = teacherName;
    }

    public String getCourseCode()   { return courseCode; }
    public String getInstanceId()   { return instanceId; }
    public String getPeriod()       { return period; }
    public String getActivityName() { return activityName; }
    public String getTeacherName()  { return teacherName; }
}
