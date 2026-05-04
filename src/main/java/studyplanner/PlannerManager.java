package studyplanner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlannerManager {

    private final Map<String, StudyItem> studyItems;
    private final Map<String, StudyPlan> plans;

    public PlannerManager() {
        this.studyItems = new LinkedHashMap<>();
        this.plans = new HashMap<>();
    }

    public void addStudyItem(StudyItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Study item cannot be null.");
        }
        studyItems.put(item.getId(), item);
    }

    public boolean removeStudyItem(String id) {
        plans.remove(id);
        return studyItems.remove(id) != null;
    }

    public StudyItem getStudyItem(String id) {
        StudyItem item = studyItems.get(id);
        if (item == null) {
            throw new NoSuchElementException("No study item found with id: " + id);
        }
        return item;
    }

    public List<StudyItem> getAllItems() {
        return new ArrayList<>(studyItems.values());
    }

    // ── stream-based queries (Lec3 – Streams) ─────────────────────────────────

    public List<StudyItem> getItemsSortedByDeadline() {
        return studyItems.values().stream()
                .sorted(Comparator.comparing(StudyItem::getDeadline))
                .collect(Collectors.toList());
    }

    public List<StudyItem> getIncompleteItems() {
        return studyItems.values().stream()
                .filter(item -> !item.isComplete())
                .collect(Collectors.toList());
    }

    public double getTotalRemainingHours() {
        return studyItems.values().stream()
                .mapToDouble(StudyItem::getRemainingHours)
                .sum();
    }

    public StudyPlan generatePlan(String id, PlanningStrategy strategy) {
        StudyItem item = getStudyItem(id);
        List<StudySession> sessions = strategy.generateSessions(item, LocalDate.now());

        // a plan is feasible if sessions exist and cover enough hours
        double covered = sessions.stream().mapToDouble(s -> s.getHoursPlanned()).sum();
        boolean feasible = !sessions.isEmpty() && covered >= item.getRemainingHours() * 0.95;

        StudyPlan plan = new StudyPlan(item, sessions, strategy.getStrategyName(), feasible);
        plans.put(id, plan);
        return plan;
    }

    public void recordProgress(String id, double hours) {
        getStudyItem(id).addProgress(hours);
    }

    public Optional<StudyPlan> getPlan(String id) {
        return Optional.ofNullable(plans.get(id));
    }
}
