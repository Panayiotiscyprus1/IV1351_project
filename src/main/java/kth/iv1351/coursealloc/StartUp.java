package kth.iv1351.coursealloc;


import kth.iv1351.coursealloc.controller.Controller;
import kth.iv1351.coursealloc.integration.DBHandler;
import kth.iv1351.coursealloc.view.CommandLineInterpreter;

/**
 * Application entry point, similar to StartUp in jdbc-intro.
 */

public class StartUp {

    public static void main(String[] args) {
        try {
            // 1. Define URL / user / password
            String url = "jdbc:postgresql://localhost:5432/iv1351_task3";
            String user = "postgres";      // <-- put your real DB user
            String password = "Panas1";  // <-- and your real DB password

            // 2. Create DBHandler
            DBHandler db = new DBHandler(url, user, password);

            // 3. (Temporary) test connection
            db.testConnection();

            // 4. Create controller & view (later)
            Controller contr = new Controller(db);
            CommandLineInterpreter cli = new CommandLineInterpreter(contr);
            cli.start();

        } catch (Exception e) {
            System.out.println("Fatal error during startup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
