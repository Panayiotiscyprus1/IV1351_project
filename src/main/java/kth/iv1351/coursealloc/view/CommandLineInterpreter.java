package kth.iv1351.coursealloc.view;


import kth.iv1351.coursealloc.controller.Controller;

/**
 * Minimal CLI placeholder just to test wiring.
 */
public class CommandLineInterpreter {
    private final Controller contr;

    public CommandLineInterpreter(Controller contr) {
        this.contr = contr;
    }

    public void start() {
        System.out.println("CLI started.");
        contr.testController();   // prove connection to controller works
        System.out.println("End of test run.");
    }
}
