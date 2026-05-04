package studyplanner;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class StudyPlan {

    private final StudyItem studyItem;
    private final List<StudySession> sessions;
    private final String strategyUsed;
    private final LocalDate generatedOn;
    private final boolean feasible;

    public StudyPlan(StudyItem studyItem, List<StudySession> sessions, String strategyUsed, boolean feasible) {
        this.studyItem = studyItem;
        this.sessions = Collections.unmodifiableList(sessions);
        this.strategyUsed = strategyUsed;
        this.generatedOn = LocalDate.now();
        this.feasible = feasible;
    }

    public StudyItem getStudyItem() { return studyItem; }
    public List<StudySession> getSessions() { return sessions; }
    public String getStrategyUsed() { return strategyUsed; }
    public LocalDate getGeneratedOn() { return generatedOn; }
    public boolean isFeasible() { return feasible; }

    public double getTotalPlannedHours() {
        return sessions.stream().mapToDouble(StudySession::getHoursPlanned).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- Plan: %s [%s strategy, generated %s] ---%n",
                studyItem.getTitle(), strategyUsed, generatedOn));
        if (!feasible) {
            sb.append("  WARNING: Not enough time before deadline!\n");
        }
        if (sessions.isEmpty()) {
            sb.append("  No sessions to schedule.\n");
        } else {
            LocalDate today = LocalDate.now();
            for (StudySession s : sessions) {
                if (s.getDate().equals(today)) {
                    sb.append(s).append("  ← TODAY\n");
                } else {
                    sb.append(s).append("\n");
                }
            }
        }
        sb.append(String.format("  Total planned: %.1f hours%n", getTotalPlannedHours()));
        return sb.toString();
    }
}
