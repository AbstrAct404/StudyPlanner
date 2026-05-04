package studyplanner;

import studyplanner.exceptions.StudyPlannerException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class CommandRouter implements AutoCloseable {

    private final Scanner scanner;

    public CommandRouter(Scanner scanner) {
        this.scanner = scanner;
    }

    // read one menu choice and dispatch it; returns false when the user exits
    public boolean routeNextCommand() {
        String choice = scanner.nextLine().trim();
        try {
            switch (choice) {
                case "1"  -> { addItem();           AppController.save(); }
                case "2"  -> viewItems();
                case "3"  -> { generatePlan();      AppController.save(); }
                case "4"  -> { recordProgress();    AppController.save(); }
                case "5"  -> { addNote();            AppController.save(); }
                case "6"  -> { editSchedule();       AppController.save(); }
                case "7"  -> studyRoadmap();
                case "8"  -> planOptimization();
                case "9"  -> { deleteItem();         AppController.save(); }
                case "10" -> { updateItem();         AppController.save(); }
                case "11" -> viewCurrentPlan();
                case "12" -> exportData();
                case "0"  -> { AppController.save(); return false; }
                default   -> System.out.println("Invalid option. Try again.");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return true;
    }

    // ── Add item ───────────────────────────────────────────────────────────────

    private void addItem() {
        System.out.print("Title: ");
        String title = InputValidator.validateTitle(scanner.nextLine());

        System.out.print("Deadline (YYYY-MM-DD): ");
        LocalDate deadline = InputValidator.validateDeadline(scanner.nextLine());

        System.out.print("Total hours needed: ");
        double hours = InputValidator.validateHours(parseDouble(scanner.nextLine()));

        System.out.print("Study days per week (1-7): ");
        int days = InputValidator.validateDaysPerWeek(parseInt(scanner.nextLine()));

        StudyItem item = new StudyItem(title, deadline, hours, days);

        // schedule mode replaces the old weekend confirmation
        configureSchedule(item);

        // optional material folder
        System.out.print("Material folder path (Enter to skip): ");
        String folder = scanner.nextLine().trim();
        if (!folder.isEmpty()) {
            scanMaterialFolder(item, folder);
        }

        AppController.addStudyItem(item);
        System.out.println("Added: " + item);
    }

    // ── View items ─────────────────────────────────────────────────────────────

    private void viewItems() {
        List<StudyItem> items = AppController.getItemsSortedByDeadline();
        if (items.isEmpty()) { System.out.println("No study items yet."); return; }
        System.out.println();
        for (int i = 0; i < items.size(); i++) {
            StudyItem it = items.get(i);
            System.out.printf("%d. %s", i + 1, it);
            if (!it.getCustomHoursPerDow().isEmpty()) {
                System.out.printf(" [custom: %s]", formatCustomPattern(it.getCustomHoursPerDow()));
            } else if (!it.getHeavierDays().isEmpty()) {
                System.out.printf(" [heavier: %s x%.1f]", formatDays(it.getHeavierDays()), it.getHeavierDayMultiplier());
            }
            if (!it.getMaterialFolder().isEmpty()) {
                System.out.printf(" [folder: %d file(s), ~%d pg(s)]",
                        it.getMaterialFileCount(), it.getEstimatedTotalPages());
            }
            System.out.println();
            List<String> notes = it.getNotes();
            if (!notes.isEmpty()) {
                System.out.println("   Notes:");
                notes.forEach(n -> System.out.println("     - " + n));
            }
        }
        System.out.printf("%nTotal remaining: %.1f h across %d incomplete item(s).%n",
                AppController.getTotalRemainingHours(), AppController.getIncompleteItems().size());
    }

    // ── Generate plan ──────────────────────────────────────────────────────────

    private void generatePlan() {
        StudyItem item = selectItem(true);
        if (item == null) return;

        System.out.println("Strategy:  1. Daily   2. Weekly");
        System.out.print("Choice: ");
        String choice = scanner.nextLine().trim();

        PlanningStrategy strategy = choice.equals("2") ? new WeeklyPlanningStrategy() : new DailyPlanningStrategy();
        StudyPlan plan = AppController.generatePlan(item.getId(), strategy);

        // if daily plan produces too many sessions, auto-switch to weekly
        if (!(strategy instanceof WeeklyPlanningStrategy) && plan.getSessions().size() > 50) {
            System.out.printf(
                "Note: daily plan would have %d sessions (too many to be useful). Switching to weekly view.%n",
                plan.getSessions().size());
            strategy = new WeeklyPlanningStrategy();
            plan = AppController.generatePlan(item.getId(), strategy);
        }

        System.out.println(plan);
    }

    // ── Record progress ────────────────────────────────────────────────────────

    private void recordProgress() {
        StudyItem item = selectItem(true);
        if (item == null) return;
        System.out.print("Hours completed this session: ");
        double hours = InputValidator.validateProgressHours(parseDouble(scanner.nextLine()));
        AppController.recordProgress(item.getId(), hours);
        System.out.printf("Saved. Total remaining for '%s': %.1f hours%n",
                item.getTitle(), item.getRemainingHours());

        // offer to mark material files as covered
        markCoveredFiles(item);

        // if item just became complete, ask whether to delete it
        if (item.isComplete()) {
            System.out.printf("'%s' is now complete! Delete it? (y/n): ", item.getTitle());
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                AppController.removeStudyItem(item.getId());
                System.out.println("Item deleted.");
                AppController.save();
                return;
            }
            System.out.println("Kept. You can still delete or update it via options 9/10.");
        }

        // auto-redistribute remaining hours across future days
        AppController.getPlan(item.getId()).ifPresent(existingPlan -> {
            if (item.isComplete()) {
                System.out.println("No sessions needed — item is complete.");
                return;
            }
            PlanningStrategy strategy = existingPlan.getStrategyUsed().equals("Weekly")
                    ? new WeeklyPlanningStrategy() : new DailyPlanningStrategy();
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            StudyPlan updated = AppController.generatePlan(item.getId(), strategy, tomorrow);

            // if daily redistribution is still too long, switch to weekly
            if (!(strategy instanceof WeeklyPlanningStrategy) && updated.getSessions().size() > 50) {
                strategy = new WeeklyPlanningStrategy();
                updated = AppController.generatePlan(item.getId(), strategy, tomorrow);
                System.out.println("Plan updated (switched to weekly — too many daily sessions):");
            } else {
                System.out.println("Plan updated — remaining hours redistributed:");
            }
            System.out.println(updated);
            printMaterialSuggestion(item, updated);
        });
    }

    /** Prompts the user to mark which material files they covered in this session. */
    private void markCoveredFiles(StudyItem item) {
        if (item.getMaterialFolder() == null || item.getMaterialFolder().isBlank()) return;
        List<String> allFiles = listFileNames(item.getMaterialFolder());
        if (allFiles.isEmpty()) return;

        // show uncovered files only
        List<String> uncovered = new ArrayList<>();
        for (String f : allFiles) {
            if (!item.isFileCovered(f)) uncovered.add(f);
        }
        if (uncovered.isEmpty()) {
            System.out.println("All material files already marked as covered.");
            return;
        }

        System.out.println("Files covered in this session? (Enter numbers separated by commas, or Enter to skip)");
        for (int i = 0; i < uncovered.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, uncovered.get(i));
        }
        System.out.print("Numbers: ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) return;

        int marked = 0;
        for (String part : input.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1;
                if (idx >= 0 && idx < uncovered.size()) {
                    item.markFileCovered(uncovered.get(idx));
                    marked++;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (marked > 0) System.out.printf("%d file(s) marked as covered.%n", marked);
    }

    // ── Add / Delete note ──────────────────────────────────────────────────────

    private void addNote() {
        StudyItem item = selectItem();
        if (item == null) return;

        List<String> existing = item.getNotes();
        if (existing.isEmpty()) {
            System.out.println("No notes yet.");
        } else {
            existing.forEach(n -> System.out.println("- (" + (existing.indexOf(n) + 1) + ") " + n));
        }

        System.out.println("Select an option:");
        System.out.println("  1. Add note");
        System.out.println("  2. Delete note");
        System.out.print("Choice (Enter to cancel): ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("1")) {
            System.out.print("New note: ");
            String note = scanner.nextLine().trim();
            if (note.isEmpty()) { System.out.println("Cancelled."); return; }
            item.addNote(note);
            System.out.println("Note added.");
        } else if (choice.equals("2")) {
            if (existing.isEmpty()) { System.out.println("No notes to delete."); return; }
            System.out.print("Delete note number: ");
            try {
                int idx = parseInt(scanner.nextLine()) - 1;
                if (item.removeNote(idx)) {
                    System.out.println("Note deleted.");
                } else {
                    System.out.println("Invalid number.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid input.");
            }
        } else {
            System.out.println("Cancelled.");
        }
    }

    // ── Edit schedule ──────────────────────────────────────────────────────────

    private void editSchedule() {
        StudyItem item = selectItem(true);
        if (item == null) return;
        configureSchedule(item);
        System.out.println("Schedule updated for: " + item.getTitle());
    }

    // ── AI: roadmap (item-selection-first, material-aware) ────────────────────

    private void studyRoadmap() {
        System.out.println("Select the study item for the AI roadmap:");
        StudyItem item = selectItem();
        if (item == null) return;

        // resolve material file names: use saved folder or ask user right now
        List<String> fileNames = new ArrayList<>();
        long totalPages = 0;

        String folder = item.getMaterialFolder();
        if (!folder.isEmpty()) {
            // item already has a folder — scan it silently
            fileNames = listFileNames(folder);
            totalPages = item.getEstimatedTotalPages();
            if (!fileNames.isEmpty()) {
                System.out.printf("Using %d material file(s) from saved folder for a detailed roadmap.%n",
                        fileNames.size());
            }
        } else {
            // no folder on record — offer to provide one just for this roadmap
            System.out.println("No material folder linked to this item.");
            System.out.print("Enter folder path for a detailed roadmap (Enter to skip): ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                fileNames = listFileNames(input);
                if (fileNames.isEmpty()) {
                    System.out.println("Folder not found or empty — generating a general roadmap.");
                } else {
                    // estimate pages from fresh scan
                    totalPages = estimatePages(input);
                    System.out.printf("Found %d file(s) — generating material-specific roadmap.%n",
                            fileNames.size());
                }
            }
        }

        String subject = item.getTitle();
        System.out.println("Building roadmap for \"" + subject + "\"...");
        if (!fileNames.isEmpty()) {
            awaitAI(AppController.getStudyRoadmapWithMaterialsAsync(subject, fileNames, totalPages));
        } else {
            awaitAI(AppController.getStudyRoadmapAsync(subject));
        }
    }

    // ── AI: optimize (item-selection-first) ───────────────────────────────────

    private void planOptimization() {
        System.out.println("Select the study item to optimize:");
        StudyItem item = selectItem();
        if (item == null) return;

        Optional<StudyPlan> planOpt = AppController.getPlan(item.getId());
        if (planOpt.isEmpty()) {
            System.out.println("No plan found for '" + item.getTitle() + "'. Generate one with option 3 first.");
            return;
        }

        List<StudySession> sessions = planOpt.get().getSessions().stream()
                .sorted(Comparator.comparing(StudySession::getDate))
                .collect(Collectors.toList());

        System.out.println("Analyzing " + sessions.size() + " session(s) for '" + item.getTitle() + "'...");
        awaitAI(AppController.getPlanOptimizationAsync(sessions));
    }

    // ── Delete item ────────────────────────────────────────────────────────────

    private void deleteItem() {
        StudyItem item = selectItem();
        if (item == null) return;
        AppController.removeStudyItem(item.getId());
        System.out.println("Deleted: " + item.getTitle());
    }

    // ── Update item ────────────────────────────────────────────────────────────

    private void updateItem() {
        StudyItem item = selectItem();
        if (item == null) return;
        System.out.println("Update:  1. Title  2. Deadline  3. Total hours  4. Days/week  5. Material folder");
        System.out.print("Choice: ");
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> {
                System.out.print("New title: ");
                item.setTitle(InputValidator.validateTitle(scanner.nextLine()));
                System.out.println("Title updated.");
            }
            case "2" -> {
                System.out.print("New deadline (YYYY-MM-DD): ");
                item.setDeadline(InputValidator.validateDeadline(scanner.nextLine()));
                System.out.println("Deadline updated.");
            }
            case "3" -> {
                System.out.print("New total hours: ");
                item.setTotalHours(InputValidator.validateHours(parseDouble(scanner.nextLine())));
                System.out.println("Total hours updated.");
            }
            case "4" -> {
                System.out.print("New days/week (1-7): ");
                item.setDaysAvailablePerWeek(InputValidator.validateDaysPerWeek(parseInt(scanner.nextLine())));
                System.out.println("Days/week updated.");
            }
            case "5" -> {
                System.out.print("Material folder path (Enter to clear): ");
                String folder = scanner.nextLine().trim();
                if (folder.isEmpty()) {
                    item.setMaterialFolder("");
                    item.setMaterialFileCount(0);
                    item.setEstimatedTotalPages(0);
                    System.out.println("Material folder cleared.");
                } else {
                    scanMaterialFolder(item, folder);
                }
            }
            default -> System.out.println("Invalid choice.");
        }
    }

    // ── View current plan ──────────────────────────────────────────────────────

    private void viewCurrentPlan() {
        StudyItem item = selectItem();
        if (item == null) return;

        // always show notes
        List<String> notes = item.getNotes();
        if (!notes.isEmpty()) {
            System.out.println("  Notes:");
            notes.forEach(n -> System.out.println("    - " + n));
        }

        // completed items: show done status only, no session details
        if (item.isComplete()) {
            System.out.printf("[DONE] '%s' — %.1f/%.1f hours completed.%n",
                    item.getTitle(), item.getHoursCompleted(), item.getTotalHours());
            return;
        }

        AppController.getPlan(item.getId()).ifPresentOrElse(
                plan -> {
                    System.out.println(plan);
                    printMaterialSuggestion(item, plan);
                },
                () -> System.out.println("No plan yet — generate one with option 3."));
    }

    // ── Export ─────────────────────────────────────────────────────────────────

    private void exportData() {
        System.out.print("Export path (folder or .csv file): ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) { System.out.println("Path required."); return; }

        Path path = Path.of(input);
        if (Files.isDirectory(path)) {
            path = path.resolve("study_export.csv");
        } else if (!input.endsWith(".csv")) {
            path = Path.of(input + ".csv");
        }

        try {
            AppController.export(path);
            System.out.println("Exported to: " + path.toAbsolutePath());
        } catch (StudyPlannerException e) {
            System.out.println("Export failed: " + e.getMessage());
        }
    }

    // ── Schedule configuration helper ─────────────────────────────────────────

    /**
     * Asks the user to choose between even-split, heavier-days, and custom-hours scheduling.
     * Updates item in-place. Selecting a mode clears the settings for the other modes.
     */
    private void configureSchedule(StudyItem item) {
        System.out.println("Schedule mode:");
        System.out.println("  1. Even split    — same hours every study day");
        System.out.println("  2. Heavier days  — specific days of the week get more time (multiplier)");
        System.out.println("  3. Custom hours  — set exact hours for each day of the week");
        System.out.print("Choice (default 1): ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("2")) {
            item.setCustomHoursPerDow(new java.util.LinkedHashMap<>());   // clear custom mode
            System.out.println("Enter heavier days (comma-separated):");
            System.out.println("  1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun");
            System.out.print("Days: ");
            String dayInput = scanner.nextLine().trim();
            Set<DayOfWeek> heavierDays = parseDaysOfWeek(dayInput);
            if (heavierDays.isEmpty()) {
                System.out.println("No valid days entered — using even split.");
                item.setHeavierDays(new HashSet<>());
                return;
            }
            item.setHeavierDays(heavierDays);
            System.out.print("Hours multiplier for heavier days (default 1.5): ");
            String multInput = scanner.nextLine().trim();
            if (!multInput.isEmpty()) {
                try {
                    item.setHeavierDayMultiplier(Double.parseDouble(multInput));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid multiplier — using 1.5.");
                    item.setHeavierDayMultiplier(1.5);
                }
            }
            System.out.printf("Heavier days set: %s (%.1fx hours)%n",
                    formatDays(item.getHeavierDays()), item.getHeavierDayMultiplier());

        } else if (choice.equals("3")) {
            item.setHeavierDays(new HashSet<>());   // clear heavier-days mode
            java.util.Map<DayOfWeek, Double> pattern = buildCustomPattern();
            if (pattern.isEmpty()) {
                System.out.println("No hours entered — using even split.");
                item.setCustomHoursPerDow(new java.util.LinkedHashMap<>());
                return;
            }
            item.setCustomHoursPerDow(pattern);
            // auto-update daysPerWeek to match the number of custom days
            item.setDaysAvailablePerWeek(pattern.size());
            System.out.print("Custom pattern saved: ");
            System.out.println(formatCustomPattern(item.getCustomHoursPerDow()));

        } else {
            item.setHeavierDays(new HashSet<>());
            item.setCustomHoursPerDow(new java.util.LinkedHashMap<>());
            System.out.println("Even split selected.");
        }
    }

    /**
     * Prompts the user to enter hours for each day of the week (Mon–Sun).
     * Days with 0 or blank input are excluded from the pattern.
     */
    private java.util.Map<DayOfWeek, Double> buildCustomPattern() {
        java.util.Map<DayOfWeek, Double> pattern = new java.util.LinkedHashMap<>();
        DayOfWeek[] order = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        System.out.println("Enter hours for each day (0 or Enter to skip that day):");
        for (DayOfWeek dow : order) {
            String label = dow.name().charAt(0) + dow.name().substring(1, 3).toLowerCase();
            System.out.printf("  %-9s: ", label);
            String input = scanner.nextLine().trim();
            if (input.isEmpty() || input.equals("0")) continue;
            try {
                double h = Double.parseDouble(input);
                if (h > 0) pattern.put(dow, h);
            } catch (NumberFormatException e) {
                System.out.println("  Invalid — skipping " + label);
            }
        }
        return pattern;
    }

    // ── Material folder scanning ───────────────────────────────────────────────

    /**
     * Scans the given folder path for files, estimates total pages from file sizes,
     * and stores the results on the item.
     * Heuristic: 1 page ≈ 50 KB for typical study PDFs/slides.
     */
    private void scanMaterialFolder(StudyItem item, String folderPath) {
        Path dir = Path.of(folderPath);
        if (!Files.isDirectory(dir)) {
            System.out.println("Folder not found or not a directory — skipping material scan.");
            return;
        }
        try {
            List<Path> files = Files.list(dir)
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
            int fileCount = files.size();
            long totalBytes = 0;
            for (Path f : files) {
                totalBytes += Files.size(f);
            }
            long estimatedPages = Math.max(1, totalBytes / (50 * 1024)); // 50 KB per page
            item.setMaterialFolder(folderPath);
            item.setMaterialFileCount(fileCount);
            item.setEstimatedTotalPages(estimatedPages);
            System.out.printf("Scanned folder: %d file(s), ~%d estimated page(s).%n",
                    fileCount, estimatedPages);
        } catch (Exception e) {
            System.out.println("Could not scan folder: " + e.getMessage());
        }
    }

    /** Returns a list of file names (not full paths) from the given folder, sorted. */
    private List<String> listFileNames(String folderPath) {
        Path dir = Path.of(folderPath);
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try {
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Estimates total pages for all files in a folder (50 KB ≈ 1 page). */
    private long estimatePages(String folderPath) {
        Path dir = Path.of(folderPath);
        if (!Files.isDirectory(dir)) return 0;
        try {
            long totalBytes = Files.list(dir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> { try { return Files.size(p); } catch (Exception e) { return 0; } })
                    .sum();
            return Math.max(1, totalBytes / (50 * 1024));
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Material suggestion helper ─────────────────────────────────────────────

    /**
     * If the item has a material folder, prints a suggestion of how many files
     * and pages the user should cover per study session.
     */
    private void printMaterialSuggestion(StudyItem item, StudyPlan plan) {
        if (item.getMaterialFileCount() <= 0) return;
        int sessions = plan.getSessions().size();
        if (sessions == 0) return;
        double filesPerSession  = (double) item.getMaterialFileCount()  / sessions;
        double pagesPerSession  = (double) item.getEstimatedTotalPages() / sessions;
        System.out.printf("  Material suggestion: ~%.1f file(s)/session, ~%.0f page(s)/session " +
                "(%d files, ~%d total pages)%n",
                filesPerSession, pagesPerSession,
                item.getMaterialFileCount(), item.getEstimatedTotalPages());
    }

    // ── Async AI helper ────────────────────────────────────────────────────────

    private void awaitAI(CompletableFuture<String> future) {
        try {
            System.out.println(future.get(36, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            System.out.println("[AI timeout] Request exceeded limit.");
            future.cancel(true);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("[AI error] " + e.getMessage());
        }
    }

    // ── Item selection helper ─────────────────────────────────────────────────

    /**
     * Prompts the user to select a study item.
     *
     * @param incompleteOnly when true, completed items are hidden from the list
     *                       (used for generate-plan, record-progress, edit-schedule)
     */
    private StudyItem selectItem(boolean incompleteOnly) {
        List<StudyItem> all = AppController.getItemsSortedByDeadline();
        List<StudyItem> items = incompleteOnly
                ? all.stream().filter(i -> !i.isComplete()).collect(java.util.stream.Collectors.toList())
                : all;
        if (items.isEmpty()) {
            System.out.println(incompleteOnly ? "No active (incomplete) study items." : "No study items.");
            return null;
        }
        System.out.println();
        for (int i = 0; i < items.size(); i++) {
            String tag = items.get(i).isComplete() ? " [DONE]" : "";
            System.out.printf("%d. %s%s%n", i + 1, items.get(i), tag);
        }
        System.out.print("Select number: ");
        try {
            int idx = parseInt(scanner.nextLine()) - 1;
            if (idx < 0 || idx >= items.size()) {
                System.out.println("Invalid selection.");
                return null;
            }
            return items.get(idx);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid input.");
            return null;
        }
    }

    /** Selects from all items (including completed). */
    private StudyItem selectItem() {
        return selectItem(false);
    }

    // ── Parsing helpers ────────────────────────────────────────────────────────

    /**
     * Parses a comma-separated list of day numbers (1=Mon … 7=Sun) into a Set<DayOfWeek>.
     */
    private Set<DayOfWeek> parseDaysOfWeek(String input) {
        Set<DayOfWeek> result = new HashSet<>();
        if (input == null || input.isBlank()) return result;
        for (String part : input.split(",")) {
            try {
                int n = Integer.parseInt(part.trim());
                if (n >= 1 && n <= 7) result.add(DayOfWeek.of(n));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private String formatDays(Set<DayOfWeek> days) {
        return days.stream()
                .sorted()
                .map(d -> d.name().charAt(0) + d.name().substring(1, 3).toLowerCase())
                .collect(Collectors.joining(", "));
    }

    private String formatCustomPattern(java.util.Map<DayOfWeek, Double> pattern) {
        return pattern.entrySet().stream()
                .map(e -> {
                    String d = e.getKey().name().charAt(0)
                            + e.getKey().name().substring(1, 3).toLowerCase();
                    return d + "=" + e.getValue() + "h";
                })
                .collect(Collectors.joining(", "));
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid number."); }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid integer."); }
    }

    @Override
    public void close() {
        scanner.close();
    }
}
