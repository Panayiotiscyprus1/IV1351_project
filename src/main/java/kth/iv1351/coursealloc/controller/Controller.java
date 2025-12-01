package kth.iv1351.coursealloc.controller;


import kth.iv1351.coursealloc.integration.DBHandler;

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
     * Temporary method just for early testing.
     * Prints confirmation that Controller was created and DBHandler injected.
     */
    public void testController() {
        System.out.println("Controller initialized successfully.");
    }
}
