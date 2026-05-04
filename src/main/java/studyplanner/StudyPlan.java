package studyplanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
            List<String> files = loadMaterialFiles();
            int numSessions = sessions.size();
            LocalDate today = LocalDate.now();

            // compress consecutive same-hours runs of 4+ into a single summary line.
            // if today falls inside a run, print today individually and compress the rest.
            int i = 0;
            while (i < numSessions) {
                // find end of current same-hours run
                double runHours = sessions.get(i).getHoursPlanned();
                int j = i + 1;
                while (j < numSessions
                        && Math.abs(sessions.get(j).getHoursPlanned() - runHours) < 0.001) {
                    j++;
                }

                // find today's index within this run (-1 if absent)
                int todayIdx = -1;
                for (int k = i; k < j; k++) {
                    if (sessions.get(k).getDate().equals(today)) { todayIdx = k; break; }
                }

                // emit a sub-range [from, to): compress if ≥ 4 sessions, else print individually
                int from = i;
                while (from < j) {
                    // decide the sub-range end: split just before and just after todayIdx
                    int to;
                    if (todayIdx >= 0 && todayIdx == from) {
                        to = from + 1;          // today alone
                    } else if (todayIdx >= 0 && todayIdx > from) {
                        to = todayIdx;          // everything before today
                    } else {
                        to = j;                 // remainder (today already passed or absent)
                    }

                    int subLen = to - from;
                    if (subLen >= 4) {
                        // compress sub-range
                        sb.append(String.format("  %s  to  %s  %.1fh/session  (%d sessions)%n",
                                sessions.get(from).getDate(), sessions.get(to - 1).getDate(),
                                runHours, subLen));
                    } else {
                        // print individually
                        for (int k = from; k < to; k++) {
                            appendSessionLine(sb, sessions.get(k), k, numSessions, files, today);
                        }
                    }
                    from = to;
                }
                i = j;
            }
        }
        sb.append(String.format("  Total planned: %.1f hours%n", getTotalPlannedHours()));
        return sb.toString();
    }

    private void appendSessionLine(StringBuilder sb, StudySession s, int idx,
                                   int numSessions, List<String> files, LocalDate today) {
        boolean weekly = "Weekly".equals(strategyUsed);

        String fileStr = "";
        if (!files.isEmpty()) {
            int perSession = Math.max(1, (int) Math.ceil((double) files.size() / numSessions));
            int fStart = idx * perSession;
            if (fStart < files.size()) {
                int fEnd = Math.min(fStart + perSession, files.size());
                StringBuilder fp = new StringBuilder();
                for (int m = fStart; m < fEnd; m++) {
                    if (m > fStart) fp.append(", ");
                    String fname = files.get(m);
                    fp.append(studyItem.isFileCovered(fname) ? "[x] " : "[ ] ").append(fname);
                }
                fileStr = fp.toString();
            }
        }

        String todayTag = s.getDate().equals(today) ? "  <- TODAY" : "";
        if (weekly) {
            if (fileStr.isEmpty()) {
                sb.append(String.format("  %s  %.1fh%s%n", s.getDate(), s.getHoursPlanned(), todayTag));
            } else {
                sb.append(String.format("  %s  %.1fh  %s%s%n", s.getDate(), s.getHoursPlanned(), fileStr, todayTag));
            }
        } else {
            String dow = s.getDate().getDayOfWeek().name();
            dow = dow.charAt(0) + dow.substring(1, 3).toLowerCase();
            if (fileStr.isEmpty()) {
                sb.append(String.format("  %s  %-3s  %.1fh%s%n", s.getDate(), dow, s.getHoursPlanned(), todayTag));
            } else {
                sb.append(String.format("  %s  %-3s  %.1fh  %s%s%n", s.getDate(), dow, s.getHoursPlanned(), fileStr, todayTag));
            }
        }
    }

    /** Lists sorted file names from the linked material folder; returns empty list if none. */
    private List<String> loadMaterialFiles() {
        String folder = studyItem.getMaterialFolder();
        if (folder == null || folder.isBlank()) return Collections.emptyList();
        try {
            Path dir = Path.of(folder);
            if (!Files.isDirectory(dir)) return Collections.emptyList();
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
