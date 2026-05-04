package studyplanner;

import studyplanner.exceptions.StudyPlannerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

// file-based persistence using NIO Path + Files (Lec6)
// failures are wrapped as StudyPlannerException so the caller decides how to recover
public class PersistenceService {

    private static final Logger log = Logger.getLogger(PersistenceService.class.getName());

    private final Path filePath;

    public PersistenceService(Path filePath) {
        this.filePath = filePath;
    }

    // ── public API ─────────────────────────────────────────────────────────────

    /** Returned by load() so the caller gets items and any persisted plans together. */
    public static class LoadResult {
        public final List<StudyItem>          items;
        public final Map<String, List<StudySession>> planSessions;   // itemId → sessions
        public final Map<String, String>      planStrategies;        // itemId → strategyName
        public final Map<String, LocalDate>   planDates;             // itemId → generatedOn
        public final Map<String, Boolean>     planFeasible;          // itemId → feasible

        public LoadResult(List<StudyItem> items,
                          Map<String, List<StudySession>> planSessions,
                          Map<String, String> planStrategies,
                          Map<String, LocalDate> planDates,
                          Map<String, Boolean> planFeasible) {
            this.items          = items;
            this.planSessions   = planSessions;
            this.planStrategies = planStrategies;
            this.planDates      = planDates;
            this.planFeasible   = planFeasible;
        }
    }

    public void save(List<StudyItem> items, Map<String, StudyPlan> plans) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < items.size(); i++) {
            StudyPlan plan = plans.get(items.get(i).getId());
            sb.append(toJson(items.get(i), plan));
            if (i < items.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        try {
            Files.writeString(filePath, sb.toString());
            log.fine("Saved " + items.size() + " item(s) → " + filePath);
        } catch (IOException ex) {
            throw new StudyPlannerException("Save failed: " + filePath, ex);
        }
    }

    public LoadResult load() {
        List<StudyItem>          items          = new ArrayList<>();
        Map<String, List<StudySession>> sessions   = new LinkedHashMap<>();
        Map<String, String>      strategies     = new LinkedHashMap<>();
        Map<String, LocalDate>   dates          = new LinkedHashMap<>();
        Map<String, Boolean>     feasible       = new LinkedHashMap<>();

        if (!Files.exists(filePath)) {
            return new LoadResult(items, sessions, strategies, dates, feasible);
        }
        try {
            String content = Files.readString(filePath);
            parse(content, items, sessions, strategies, dates, feasible);
        } catch (IOException ex) {
            throw new StudyPlannerException("Load failed: " + filePath, ex);
        }
        return new LoadResult(items, sessions, strategies, dates, feasible);
    }

    // ── serialisation helpers ──────────────────────────────────────────────────

    private String toJson(StudyItem it, StudyPlan plan) {
        // notes serialised as a JSON array of strings
        StringBuilder notesJson = new StringBuilder("[");
        List<String> notes = it.getNotes();
        for (int i = 0; i < notes.size(); i++) {
            notesJson.append("\"").append(escape(notes.get(i))).append("\"");
            if (i < notes.size() - 1) notesJson.append(",");
        }
        notesJson.append("]");

        // customHoursPerDow serialised as "MONDAY=2.0,WEDNESDAY=3.0,..."
        StringBuilder customHoursStr = new StringBuilder();
        boolean firstEntry = true;
        for (Map.Entry<DayOfWeek, Double> e : it.getCustomHoursPerDow().entrySet()) {
            if (!firstEntry) customHoursStr.append(",");
            customHoursStr.append(e.getKey().name()).append("=").append(e.getValue());
            firstEntry = false;
        }

        // heavierDays serialised as comma-separated DOW names
        StringBuilder heavierDaysStr = new StringBuilder();
        boolean first = true;
        for (DayOfWeek dow : it.getHeavierDays()) {
            if (!first) heavierDaysStr.append(",");
            heavierDaysStr.append(dow.name());
            first = false;
        }

        // coveredFiles serialised as a JSON array of strings
        StringBuilder coveredJson = new StringBuilder("[");
        List<String> coveredList = new ArrayList<>(it.getCoveredFiles());
        for (int i = 0; i < coveredList.size(); i++) {
            coveredJson.append("\"").append(escape(coveredList.get(i))).append("\"");
            if (i < coveredList.size() - 1) coveredJson.append(",");
        }
        coveredJson.append("]");

        // plan sessions serialised as a JSON array of {date, hours} objects
        StringBuilder sessionsJson = new StringBuilder("[");
        String planStrategy  = "";
        String planGenerated = "";
        boolean planFeasible = false;
        if (plan != null) {
            planStrategy  = plan.getStrategyUsed();
            planGenerated = plan.getGeneratedOn().toString();
            planFeasible  = plan.isFeasible();
            List<StudySession> ss = plan.getSessions();
            for (int i = 0; i < ss.size(); i++) {
                StudySession s = ss.get(i);
                sessionsJson.append(String.format("{\"date\":\"%s\",\"hours\":%s}",
                        s.getDate(), s.getHoursPlanned()));
                if (i < ss.size() - 1) sessionsJson.append(",");
            }
        }
        sessionsJson.append("]");

        return String.format(
                "  {\"title\":\"%s\",\"deadline\":\"%s\",\"totalHours\":%s," +
                "\"hoursCompleted\":%s,\"daysPerWeek\":%d," +
                "\"heavierDays\":\"%s\",\"heavierDayMultiplier\":%s," +
                "\"customHours\":\"%s\"," +
                "\"notes\":%s," +
                "\"materialFolder\":\"%s\",\"materialFileCount\":%d,\"estimatedTotalPages\":%d," +
                "\"coveredFiles\":%s," +
                "\"planStrategy\":\"%s\",\"planGeneratedOn\":\"%s\",\"planFeasible\":%b," +
                "\"planSessions\":%s}",
                escape(it.getTitle()), it.getDeadline(),
                it.getTotalHours(), it.getHoursCompleted(), it.getDaysAvailablePerWeek(),
                heavierDaysStr, it.getHeavierDayMultiplier(),
                customHoursStr,
                notesJson,
                escape(it.getMaterialFolder()), it.getMaterialFileCount(), it.getEstimatedTotalPages(),
                coveredJson,
                planStrategy, planGenerated, planFeasible,
                sessionsJson);
    }

    private void parse(String json,
                       List<StudyItem>          items,
                       Map<String, List<StudySession>> planSessions,
                       Map<String, String>      planStrategies,
                       Map<String, LocalDate>   planDates,
                       Map<String, Boolean>     planFeasible) {
        int depth = 0, start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth++ == 0) start = i;
            } else if (c == '}') {
                if (--depth == 0 && start >= 0) {
                    String obj = json.substring(start, i + 1);
                    StudyItem item = parseObject(obj);
                    if (item != null) {
                        items.add(item);
                        // read plan fields
                        String strategy = getStringOrDefault(obj, "planStrategy", "");
                        String genOn    = getStringOrDefault(obj, "planGeneratedOn", "");
                        boolean feasible = getBooleanOrDefault(obj, "planFeasible", true);
                        String sessArr  = getJsonArray(obj, "planSessions");
                        if (!strategy.isEmpty() && !sessArr.isEmpty()) {
                            List<StudySession> sessions = parsePlanSessions(sessArr, item.getTitle());
                            if (!sessions.isEmpty()) {
                                planSessions.put(item.getId(), sessions);
                                planStrategies.put(item.getId(), strategy);
                                planDates.put(item.getId(),
                                        genOn.isEmpty() ? LocalDate.now() : LocalDate.parse(genOn));
                                planFeasible.put(item.getId(), feasible);
                            }
                        }
                    }
                    start = -1;
                }
            }
        }
    }

    private List<StudySession> parsePlanSessions(String arrayContent, String itemTitle) {
        List<StudySession> result = new ArrayList<>();
        // arrayContent is like {"date":"2026-05-04","hours":2.5},{"date":"...","hours":...}
        int depth = 0, start = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') {
                if (--depth == 0 && start >= 0) {
                    String obj = arrayContent.substring(start, i + 1);
                    try {
                        LocalDate date  = LocalDate.parse(getString(obj, "date"));
                        double hours    = getDouble(obj, "hours");
                        result.add(new StudySession(date, itemTitle, hours));
                    } catch (Exception ignored) {}
                    start = -1;
                }
            }
        }
        return result;
    }

    private StudyItem parseObject(String obj) {
        try {
            String title       = getString(obj, "title");
            LocalDate deadline = LocalDate.parse(getString(obj, "deadline"));
            double total       = getDouble(obj, "totalHours");
            double done        = getDouble(obj, "hoursCompleted");
            int days           = (int) getDouble(obj, "daysPerWeek");

            StudyItem item = new StudyItem(title, deadline, total, days);
            if (done > 0) item.addProgress(done);

            // customHoursPerDow — safe default: empty map
            String customHoursRaw = getStringOrDefault(obj, "customHours", "");
            if (!customHoursRaw.isBlank()) {
                Map<DayOfWeek, Double> customMap = new LinkedHashMap<>();
                for (String entry : customHoursRaw.split(",")) {
                    String[] parts = entry.trim().split("=");
                    if (parts.length == 2) {
                        try {
                            DayOfWeek dow = DayOfWeek.valueOf(parts[0].trim());
                            double h = Double.parseDouble(parts[1].trim());
                            if (h > 0) customMap.put(dow, h);
                        } catch (Exception ignored) {}
                    }
                }
                item.setCustomHoursPerDow(customMap);
            }

            // heavierDays — safe default: empty set (even split)
            String heavierDaysRaw = getStringOrDefault(obj, "heavierDays", "");
            if (!heavierDaysRaw.isBlank()) {
                Set<DayOfWeek> heavierDays = new HashSet<>();
                for (String part : heavierDaysRaw.split(",")) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        try { heavierDays.add(DayOfWeek.valueOf(p)); }
                        catch (IllegalArgumentException ignored) {}
                    }
                }
                item.setHeavierDays(heavierDays);
            }
            item.setHeavierDayMultiplier(getDoubleOrDefault(obj, "heavierDayMultiplier", 1.5));

            // backward compat: old files had studyOnWeekends flag
            if (getBooleanOrDefault(obj, "studyOnWeekends", false)) {
                Set<DayOfWeek> wknd = new HashSet<>();
                wknd.add(DayOfWeek.SATURDAY);
                wknd.add(DayOfWeek.SUNDAY);
                item.setHeavierDays(wknd);
                item.setHeavierDayMultiplier(getDoubleOrDefault(obj, "weekendMultiplier", 1.5));
            }

            // notes array
            String notesSection = getJsonArray(obj, "notes");
            if (!notesSection.isEmpty()) {
                for (String n : splitJsonStringArray(notesSection)) {
                    item.addNote(n);
                }
            }

            // material folder info
            item.setMaterialFolder(getStringOrDefault(obj, "materialFolder", ""));
            item.setMaterialFileCount((int) getDoubleOrDefault(obj, "materialFileCount", 0));
            item.setEstimatedTotalPages((long) getDoubleOrDefault(obj, "estimatedTotalPages", 0));

            // covered files array
            String coveredSection = getJsonArray(obj, "coveredFiles");
            if (!coveredSection.isEmpty()) {
                for (String f : splitJsonStringArray(coveredSection)) {
                    item.markFileCovered(f);
                }
            }

            return item;
        } catch (Exception ex) {
            log.warning("Skipped malformed record: " + ex.getMessage());
            return null;
        }
    }

    // ── JSON extraction helpers ────────────────────────────────────────────────

    private String getString(String json, String key) {
        String tok = "\"" + key + "\":\"";
        int s = json.indexOf(tok) + tok.length();
        int e = json.indexOf('"', s);
        return json.substring(s, e);
    }

    private String getStringOrDefault(String json, String key, String def) {
        String tok = "\"" + key + "\":\"";
        int idx = json.indexOf(tok);
        if (idx == -1) return def;
        int s = idx + tok.length();
        int e = json.indexOf('"', s);
        if (e == -1) return def;
        return json.substring(s, e);
    }

    private double getDouble(String json, String key) {
        String tok = "\"" + key + "\":";
        int s = json.indexOf(tok) + tok.length();
        int e = s;
        while (e < json.length() && "0123456789.-".indexOf(json.charAt(e)) >= 0) e++;
        return Double.parseDouble(json.substring(s, e));
    }

    private double getDoubleOrDefault(String json, String key, double def) {
        String tok = "\"" + key + "\":";
        int idx = json.indexOf(tok);
        if (idx == -1) return def;
        int s = idx + tok.length();
        int e = s;
        while (e < json.length() && "0123456789.-".indexOf(json.charAt(e)) >= 0) e++;
        try { return Double.parseDouble(json.substring(s, e)); }
        catch (NumberFormatException ex) { return def; }
    }

    private boolean getBooleanOrDefault(String json, String key, boolean def) {
        String tok = "\"" + key + "\":";
        int idx = json.indexOf(tok);
        if (idx == -1) return def;
        String rest = json.substring(idx + tok.length()).stripLeading();
        if (rest.startsWith("true"))  return true;
        if (rest.startsWith("false")) return false;
        return def;
    }

    // returns the raw content between the outermost [ ] for the given key, or ""
    private String getJsonArray(String json, String key) {
        String tok = "\"" + key + "\":[";
        int idx = json.indexOf(tok);
        if (idx == -1) return "";
        int start = idx + tok.length() - 1; // position of '['
        int depth = 0, end = start;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }
        return json.substring(start + 1, end).trim(); // content between [ ]
    }

    // splits a JSON string array content (e.g. "\"a\",\"b\"") into individual strings
    private List<String> splitJsonStringArray(String arrayContent) {
        List<String> result = new ArrayList<>();
        if (arrayContent.isBlank()) return result;
        int i = 0;
        while (i < arrayContent.length()) {
            if (arrayContent.charAt(i) == '"') {
                int end = i + 1;
                while (end < arrayContent.length()) {
                    if (arrayContent.charAt(end) == '\\') { end += 2; continue; }
                    if (arrayContent.charAt(end) == '"') break;
                    end++;
                }
                result.add(arrayContent.substring(i + 1, end)
                        .replace("\\\"", "\"").replace("\\\\", "\\"));
                i = end + 1;
            } else {
                i++;
            }
        }
        return result;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
