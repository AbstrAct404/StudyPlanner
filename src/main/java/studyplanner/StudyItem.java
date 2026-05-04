package studyplanner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StudyItem {

    private final String id;
    private String title;
    private LocalDate deadline;
    private double totalHours;
    private double hoursCompleted;
    private int daysAvailablePerWeek;

    // distribution mode: empty set = even; non-empty = those days get heavierDayMultiplier × hours
    private Set<DayOfWeek> heavierDays          = new HashSet<>();
    private double          heavierDayMultiplier = 1.5;

    // custom mode: day-of-week → exact hours for that day (empty = not in custom mode)
    // LinkedHashMap preserves Mon→Sun insertion order for display
    private Map<DayOfWeek, Double> customHoursPerDow = new LinkedHashMap<>();

    // today's progress — transient, not persisted, resets automatically on a new day
    private double    hoursLoggedToday = 0;
    private LocalDate loggedDate       = LocalDate.now();

    // notes attached to this item's plan (persisted)
    private final List<String> notes = new ArrayList<>();

    // optional material folder info (persisted)
    private String materialFolder      = "";
    private int    materialFileCount   = 0;
    private long   estimatedTotalPages = 0;

    public StudyItem(String title, LocalDate deadline, double totalHours, int daysAvailablePerWeek) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.title = title;
        this.deadline = deadline;
        this.totalHours = totalHours;
        this.hoursCompleted = 0;
        this.daysAvailablePerWeek = daysAvailablePerWeek;
    }

    // ── core fields ────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }
    public double getHoursCompleted() { return hoursCompleted; }
    public int getDaysAvailablePerWeek() { return daysAvailablePerWeek; }
    public void setDaysAvailablePerWeek(int days) { this.daysAvailablePerWeek = days; }

    // ── distribution mode ──────────────────────────────────────────────────────

    public Set<DayOfWeek> getHeavierDays() { return Collections.unmodifiableSet(heavierDays); }
    public void setHeavierDays(Set<DayOfWeek> days) { this.heavierDays = new HashSet<>(days); }
    public double getHeavierDayMultiplier() { return heavierDayMultiplier; }
    public void setHeavierDayMultiplier(double m) {
        if (m <= 0) throw new IllegalArgumentException("Multiplier must be positive.");
        this.heavierDayMultiplier = m;
    }

    public Map<DayOfWeek, Double> getCustomHoursPerDow() {
        return Collections.unmodifiableMap(customHoursPerDow);
    }
    public void setCustomHoursPerDow(Map<DayOfWeek, Double> map) {
        this.customHoursPerDow = new LinkedHashMap<>(map);
    }

    // ── progress ───────────────────────────────────────────────────────────────

    public double getRemainingHours() {
        return Math.max(0, totalHours - hoursCompleted);
    }

    public boolean isComplete() {
        return hoursCompleted >= totalHours;
    }

    public long getDaysUntilDeadline() {
        return ChronoUnit.DAYS.between(LocalDate.now(), deadline);
    }

    public void addProgress(double hours) {
        if (hours < 0) throw new IllegalArgumentException("Progress hours cannot be negative.");
        // reset daily counter when the date changes
        LocalDate today = LocalDate.now();
        if (!today.equals(loggedDate)) {
            hoursLoggedToday = 0;
            loggedDate = today;
        }
        hoursLoggedToday = Math.min(hoursLoggedToday + hours, totalHours - hoursCompleted);
        this.hoursCompleted = Math.min(this.hoursCompleted + hours, this.totalHours);
    }

    public double getHoursLoggedToday() { return hoursLoggedToday; }

    // ── notes ──────────────────────────────────────────────────────────────────

    public void addNote(String note) {
        if (note != null && !note.isBlank()) {
            // strip pipe characters to keep serialization simple
            notes.add(note.replace("|", "").trim());
        }
    }

    public List<String> getNotes() { return Collections.unmodifiableList(notes); }

    // ── material folder ────────────────────────────────────────────────────────

    public String getMaterialFolder() { return materialFolder; }
    public void setMaterialFolder(String path) { this.materialFolder = path; }
    public int getMaterialFileCount() { return materialFileCount; }
    public void setMaterialFileCount(int n) { this.materialFileCount = n; }
    public long getEstimatedTotalPages() { return estimatedTotalPages; }
    public void setEstimatedTotalPages(long p) { this.estimatedTotalPages = p; }

    // ── display ────────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("[%s] %s | Due: %s | %.1f/%.1fh done | %d day(s)/week",
                id, title, deadline, hoursCompleted, totalHours, daysAvailablePerWeek);
    }
}
