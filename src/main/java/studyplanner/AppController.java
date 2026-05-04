package studyplanner;

import studyplanner.exceptions.StudyPlannerException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AppController {

    private static PlannerManager manager;
    private static AIStudyAssistant ai;
    private static PersistenceService persistence;
    private static ExportService exportService;

    private AppController() {}

    public static void initialize() {
        manager       = new PlannerManager();
        ai            = new AIStudyAssistant(System.getenv("ANTHROPIC_API_KEY"));
        persistence   = new PersistenceService(Path.of("study_planner_data.json"));
        exportService = new ExportService();

        // load saved data on startup; failures are non-fatal (Lec6 — NIO)
        try {
            persistence.load().forEach(manager::addStudyItem);
            int n = manager.getAllItems().size();
            if (n > 0) System.out.println("Loaded " + n + " saved item(s).");
        } catch (StudyPlannerException e) {
            System.out.println("Note: could not load saved data (" + e.getMessage() + ")");
        }
    }

    // write current items to disk; called after any mutation
    public static void save() {
        try {
            persistence.save(manager.getAllItems());
        } catch (StudyPlannerException e) {
            System.out.println("Warning: could not save data (" + e.getMessage() + ")");
        }
    }

    // simple pass-through methods to the services; menu calls these, not the services directly

    public static void addStudyItem(StudyItem item) {
        manager.addStudyItem(item);
    }

    public static boolean removeStudyItem(String id) {
        return manager.removeStudyItem(id);
    }

    public static List<StudyItem> getAllItems() {
        return manager.getAllItems();
    }

    public static List<StudyItem> getItemsSortedByDeadline() {
        return manager.getItemsSortedByDeadline();
    }

    public static List<StudyItem> getIncompleteItems() {
        return manager.getIncompleteItems();
    }

    public static double getTotalRemainingHours() {
        return manager.getTotalRemainingHours();
    }

    public static StudyPlan generatePlan(String id, PlanningStrategy strategy) {
        return manager.generatePlan(id, strategy);
    }

    public static StudyPlan generatePlan(String id, PlanningStrategy strategy, LocalDate startDate) {
        return manager.generatePlan(id, strategy, startDate);
    }

    public static void recordProgress(String id, double hours) {
        manager.recordProgress(id, hours);
    }

    public static Optional<StudyPlan> getPlan(String id) {
        return manager.getPlan(id);
    }

    // collect all generated plans and write them plus item progress to a CSV
    public static void export(Path path) {
        Map<String, StudyPlan> plans = new HashMap<>();
        for (StudyItem item : manager.getAllItems()) {
            manager.getPlan(item.getId()).ifPresent(p -> plans.put(item.getId(), p));
        }
        exportService.export(manager.getAllItems(), plans, path);
    }

    // AI features — all async so the console stays responsive during API calls
    public static CompletableFuture<String> getStudyRoadmapAsync(String subject) {
        return ai.getStudyRoadmapAsync(subject);
    }

    public static CompletableFuture<String> getPlanOptimizationAsync(List<StudySession> sessions) {
        return ai.getPlanOptimizationAsync(sessions);
    }
}
