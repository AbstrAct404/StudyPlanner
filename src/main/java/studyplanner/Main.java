package studyplanner;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.println("==========================");
        System.out.println("       Study Planner      ");
        System.out.println("==========================");

        // wire up all services and load saved data
        AppController.initialize();

        CommandRouter router = new CommandRouter(new Scanner(System.in));
        boolean running = true;
        while (running) {
            printMenu();
            running = router.routeNextCommand();
        }

        router.close();
        System.out.println("Goodbye!");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("──────── Study Planner ────────");
        System.out.println("1.  Add study item");
        System.out.println("2.  View all items");
        System.out.println("3.  Generate plan");
        System.out.println("4.  Record progress");
        System.out.println("5.  Add note to study item");
        System.out.println("6.  Edit daily schedule");
        System.out.println("──────── AI Features ───────────");
        System.out.println("7.  AI: Study roadmap (for selected item)");
        System.out.println("8.  AI: Optimize plan (for selected item)");
        System.out.println("──────── Manage Items ──────────");
        System.out.println("9.  Delete item");
        System.out.println("10. Update item");
        System.out.println("11. View current plan");
        System.out.println("12. Export to CSV");
        System.out.println("0.  Exit");
        System.out.print("Choice: ");
    }
}
