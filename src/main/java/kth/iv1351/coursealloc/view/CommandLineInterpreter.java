package kth.iv1351.coursealloc.view;

import kth.iv1351.coursealloc.controller.Controller;
import kth.iv1351.coursealloc.model.CourseInstanceCost;
import kth.iv1351.coursealloc.model.ExerciseAllocationInfo;

import java.sql.SQLException;
import java.util.Scanner;

public class CommandLineInterpreter {
    private final Controller contr;
    private final Scanner in = new Scanner(System.in);

    public CommandLineInterpreter(Controller contr) {
        this.contr = contr;
    }

    public void start() {
        printHelp();
        while (true) {
            System.out.print("> ");
            String line = in.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] tokens = line.split("\\s+");
            String cmd = tokens[0].toLowerCase();

            try {
                switch (cmd) {
                    case "quit":
                        System.out.println("Bye!");
                        return;

                    case "help":
                        printHelp();
                        break;

                    case "cost":
                        handleCost(tokens);
                        break;
                    
                    case "inc_students":
                        handleIncreaseStudents(tokens);
                        break;

                    case "add_exercise":
                        handleAddExercise(tokens);
                        break;

                    // later: allocate, deallocate, add_exercise, list_exercise  

                    default:
                        System.out.println("Unknown command. Type 'help'.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e);
                e.printStackTrace();
            }
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  cost <instance_id>");
        System.out.println("  inc_students <instance_id> <delta>");
        System.out.println("  add_exercise <instance_id> <employment_id> <planned_hours>");
        System.out.println("  help");
        System.out.println("  quit");
    }

    private void handleCost(String[] tokens) throws SQLException {
        if (tokens.length != 2) {
            System.out.println("Usage: cost <instance_id>");
            return;
        }
        String instanceId = tokens[1];
    
        CourseInstanceCost cost = contr.computeCourseCost(instanceId);
    
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("| %-11s | %-15s | %-6s | %-21s | %-21s |%n",
            "Course Code",
            "Course Instance",
            "Period",
            "Planned Cost (in KSEK)",
            "Actual Cost (in KSEK)");
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("| %-11s | %-15s | %-6s | %-21.0f | %-21.0f |%n",
            cost.getCourseCode(),
            cost.getInstanceId(),
            cost.getPeriod(),
            cost.getPlannedCostKsek(),
            cost.getActualCostKsek());
        System.out.println("-----------------------------------------------------------------------------------------");
    }

    /**
 * Command:
 *   inc_students <instance_id> <delta>
 *
 * Example:
 *   inc_students 2025-50413 100
 *
 * Increases num_students by <delta> and prints the updated number
 * of students for that instance.
 *
 * If the user wants to see how the teaching cost changed, they can run:
 *   cost <instance_id>
 * afterwards.
 */
    private void handleIncreaseStudents(String[] tokens) throws SQLException {
        if (tokens.length != 3) {
            System.out.println("Usage: inc_students <instance_id> <delta>");
            return;
        }

        String instanceId = tokens[1];
        int delta;
        try {
            delta = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            System.out.println("Delta must be an integer, e.g. 100 or -20");
            return;
        }

        int newNumStudents = contr.increaseStudents(instanceId, delta);

        System.out.println("Number of students for instance " + instanceId
                + " has been changed by " + delta + ".");
        System.out.println("New number of students: " + newNumStudents);
        System.out.println("Run 'cost " + instanceId + "' to see the updated teaching cost.");
    }


    /**
 * Command:
 *   add_exercise <instance_id> <employment_id> <planned_hours>
 *
 * Example:
 *   add_exercise 2025-50273 12345 10
 *
 * This will:
 *   - ensure "Exercise" activity exists,
 *   - add/update planned_activity for that instance,
 *   - allocate the specified teacher to Exercise,
 *   - print a summary of the affected course instance and teacher.
 */
private void handleAddExercise(String[] tokens) throws SQLException {
    if (tokens.length != 4) {
        System.out.println("Usage: add_exercise <instance_id> <employment_id> <planned_hours>");
        return;
    }

    String instanceId   = tokens[1];
    String employmentId = tokens[2];
    double plannedHours;

    try {
        plannedHours = Double.parseDouble(tokens[3]);
    } catch (NumberFormatException e) {
        System.out.println("planned_hours must be a number (e.g. 10 or 7.5).");
        return;
    }

    ExerciseAllocationInfo info = contr.addExercise(instanceId, employmentId, plannedHours);

    System.out.println("Exercise activity added/updated and teacher allocated.");
    System.out.println("Affected allocation:");
    
    System.out.println("------------------------------------------------------------------------------");
    System.out.printf("| %-11s | %-15s | %-6s | %-10s | %-20s |%n",
            "Course Code",
            "Instance ID",
            "Period",
            "Activity",
            "Teacher");
    System.out.println("------------------------------------------------------------------------------");
    System.out.printf("| %-11s | %-15s | %-6s | %-10s | %-20s |%n",
            info.getCourseCode(),
            info.getInstanceId(),
            info.getPeriod(),
            info.getActivityName(),
            info.getTeacherName());
    System.out.println("------------------------------------------------------------------------------");
}
}