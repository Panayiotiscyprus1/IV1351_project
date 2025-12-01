package kth.iv1351.coursealloc.view;

import kth.iv1351.coursealloc.controller.Controller;
import kth.iv1351.coursealloc.model.CourseInstanceCost;

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

                    // later: inc_students, allocate, deallocate, add_exercise, list_exercise

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
}