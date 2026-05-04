package studyplanner;

import studyplanner.exceptions.StudyPlannerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// exports study items and planned sessions to a single CSV file
// ITEM rows capture progress; SESSION rows capture the future schedule
public class ExportService {

    public void export(List<StudyItem> items, Map<String, StudyPlan> plans, Path path) {
        // create parent directories if they don't exist
        Path parent = path.getParent();
        try {
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, buildCsv(items, plans));
        } catch (IOException ex) {
            throw new StudyPlannerException("Export failed: " + path, ex);
        }
    }

    private String buildCsv(List<StudyItem> items, Map<String, StudyPlan> plans) {
        StringBuilder sb = new StringBuilder();
        sb.append("Type,Title,Deadline,TotalHours,Completed,Remaining,DaysPerWeek,Weekends,SessionDate,SessionHours,Strategy\n");

        for (StudyItem item : items) {
            // one ITEM row per study goal — captures progress state
            sb.append(String.format("ITEM,%s,%s,%.1f,%.1f,%.1f,%d,%s,,,\n",
                    csv(item.getTitle()),
                    item.getDeadline(),
                    item.getTotalHours(),
                    item.getHoursCompleted(),
                    item.getRemainingHours(),
                    item.getDaysAvailablePerWeek(),
                    item.isStudyOnWeekends() ? "Yes" : "No"));

            // one SESSION row per planned date — captures the future schedule
            StudyPlan plan = plans.get(item.getId());
            if (plan != null) {
                for (StudySession s : plan.getSessions()) {
                    sb.append(String.format("SESSION,%s,,,,,,,%s,%.1f,%s\n",
                            csv(item.getTitle()),
                            s.getDate(),
                            s.getHoursPlanned(),
                            plan.getStrategyUsed()));
                }
            }
        }
        return sb.toString();
    }

    // wraps a value in quotes if it contains a comma, quote, or newline
    private String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
