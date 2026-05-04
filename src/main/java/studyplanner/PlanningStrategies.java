package studyplanner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Strategy pattern (Lec2 — OOP): one interface, two concrete scheduling algorithms.
// Both implementations are package-private; callers reference the interface type.

interface PlanningStrategy {
    List<StudySession> generateSessions(StudyItem item, LocalDate startDate);
    String getStrategyName();
}

// Distributes hours evenly across individual study days respecting the days-per-week cap.
// When heavierDays is set, those specific days-of-week always appear in the schedule and
// receive heavierDayMultiplier × the base session length.
class DailyPlanningStrategy implements PlanningStrategy {

    @Override
    public List<StudySession> generateSessions(StudyItem item, LocalDate startDate) {
        double remaining = item.getRemainingHours();
        if (remaining <= 0) return new ArrayList<>();

        // custom per-DOW hours take highest priority
        if (!item.getCustomHoursPerDow().isEmpty()) {
            return generateCustomHoursSessions(item, startDate, remaining);
        }

        Set<DayOfWeek> heavierDays = item.getHeavierDays();
        if (!heavierDays.isEmpty()) {
            return generateHeavierDaySessions(item, startDate, remaining);
        }

        // even-split path (backward compatible — all existing tests use this path)
        List<LocalDate> studyDays = computeStudyDays(startDate, item.getDeadline(), item.getDaysAvailablePerWeek());
        if (studyDays.isEmpty()) return new ArrayList<>();

        double hoursPerDay = remaining / studyDays.size();

        // enforce minimum 1h per session: use fewer days with 1h each instead of many days with tiny amounts
        if (hoursPerDay < 1.0) {
            int neededDays = (int) Math.ceil(remaining);          // e.g. 10.5h → 11 sessions
            studyDays = studyDays.subList(0, Math.min(neededDays, studyDays.size()));
            hoursPerDay = remaining / studyDays.size();           // recompute over the shorter list
        }

        hoursPerDay = Math.round(hoursPerDay * 10.0) / 10.0;
        List<StudySession> sessions = new ArrayList<>();
        for (LocalDate day : studyDays) {
            sessions.add(new StudySession(day, item.getTitle(), hoursPerDay));
        }
        return sessions;
    }

    /**
     * Custom per-day-of-week mode.
     * Iterates day-by-day from startDate to deadline; whenever a day's DOW is in the
     * custom map, schedules a session with exactly those hours — capped to whatever
     * remaining hours are still needed. Stops once remaining hours are fully covered.
     */
    private List<StudySession> generateCustomHoursSessions(
            StudyItem item, LocalDate startDate, double remaining) {

        Map<DayOfWeek, Double> pattern = item.getCustomHoursPerDow();
        List<StudySession> sessions = new ArrayList<>();
        double scheduled = 0;
        LocalDate current = startDate;

        while (!current.isAfter(item.getDeadline()) && scheduled < remaining - 0.001) {
            Double h = pattern.get(current.getDayOfWeek());
            if (h != null && h > 0) {
                double actual = Math.round(Math.min(h, remaining - scheduled) * 10.0) / 10.0;
                sessions.add(new StudySession(current, item.getTitle(), actual));
                scheduled += h;
            }
            current = current.plusDays(1);
        }
        return sessions;
    }

    /**
     * Heavier-days algorithm:
     *   - Every occurrence of a heavierDay DOW between startDate and deadline is always scheduled.
     *   - The remaining (daysPerWeek - heavyDaysThisWeek) slots per 7-day window are filled
     *     with non-heavy days in calendar order.
     *   - base = remaining / (regularCount + heavyCount * multiplier)
     *   - Heavy sessions get Math.round(base * multiplier * 10) / 10 hours.
     */
    private List<StudySession> generateHeavierDaySessions(
            StudyItem item, LocalDate startDate, double remaining) {

        Set<DayOfWeek> heavierDays  = item.getHeavierDays();
        double          multiplier  = item.getHeavierDayMultiplier();
        int             daysPerWeek = item.getDaysAvailablePerWeek();
        LocalDate       deadline    = item.getDeadline();

        List<LocalDate> heavySchedule   = new ArrayList<>();
        List<LocalDate> regularSchedule = new ArrayList<>();

        LocalDate windowStart = startDate;
        while (!windowStart.isAfter(deadline)) {
            LocalDate windowEnd = windowStart.plusDays(6);
            if (windowEnd.isAfter(deadline)) windowEnd = deadline;

            int regularSlotsUsed = 0;
            // count heavy occurrences in this window to know regular capacity
            int heavyInWindow = 0;
            for (LocalDate d = windowStart; !d.isAfter(windowEnd); d = d.plusDays(1)) {
                if (heavierDays.contains(d.getDayOfWeek())) heavyInWindow++;
            }
            int regularCapacity = Math.max(0, daysPerWeek - heavyInWindow);

            for (LocalDate d = windowStart; !d.isAfter(windowEnd); d = d.plusDays(1)) {
                if (heavierDays.contains(d.getDayOfWeek())) {
                    heavySchedule.add(d);
                } else if (regularSlotsUsed < regularCapacity) {
                    regularSchedule.add(d);
                    regularSlotsUsed++;
                }
            }
            windowStart = windowStart.plusDays(7);
        }

        if (heavySchedule.isEmpty() && regularSchedule.isEmpty()) return new ArrayList<>();

        double divisor = regularSchedule.size() + heavySchedule.size() * multiplier;
        if (divisor <= 0) return new ArrayList<>();

        double baseHours   = Math.round((remaining / divisor) * 10.0) / 10.0;
        double heavyHours  = Math.round(baseHours * multiplier * 10.0) / 10.0;

        List<StudySession> sessions = new ArrayList<>();
        for (LocalDate d : heavySchedule) {
            sessions.add(new StudySession(d, item.getTitle(), heavyHours));
        }
        for (LocalDate d : regularSchedule) {
            sessions.add(new StudySession(d, item.getTitle(), baseHours));
        }
        sessions.sort(Comparator.comparing(StudySession::getDate));
        return sessions;
    }

    // picks the first daysPerWeek days of each 7-day window starting from startDate
    private List<LocalDate> computeStudyDays(LocalDate start, LocalDate deadline, int daysPerWeek) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate current = start;
        long currentWeekIndex = 0;
        int dayCountThisWeek = 0;

        while (!current.isAfter(deadline)) {
            long weekIndex = ChronoUnit.WEEKS.between(start, current);
            if (weekIndex != currentWeekIndex) {
                currentWeekIndex = weekIndex;
                dayCountThisWeek = 0;
            }
            if (dayCountThisWeek < daysPerWeek) {
                days.add(current);
                dayCountThisWeek++;
            }
            current = current.plusDays(1);
        }
        return days;
    }

    @Override
    public String getStrategyName() {
        return "Daily";
    }
}

// Distributes hours as one target session per calendar week.
class WeeklyPlanningStrategy implements PlanningStrategy {

    private static final double MIN_HOURS_PER_WEEK = 7.0;

    @Override
    public List<StudySession> generateSessions(StudyItem item, LocalDate startDate) {
        List<StudySession> sessions = new ArrayList<>();
        double remaining = item.getRemainingHours();
        if (remaining <= 0) return sessions;

        long totalDays = ChronoUnit.DAYS.between(startDate, item.getDeadline()) + 1;
        if (totalDays <= 0) return sessions;

        long totalWeeks = Math.max(1, (totalDays + 6) / 7);
        double hoursPerWeek = remaining / totalWeeks;

        // enforce minimum 7h/week — use fewer weeks instead of tiny sessions
        if (hoursPerWeek < MIN_HOURS_PER_WEEK) {
            totalWeeks = (long) Math.ceil(remaining / MIN_HOURS_PER_WEEK);
            hoursPerWeek = remaining / totalWeeks;
        }

        hoursPerWeek = Math.round(hoursPerWeek * 10.0) / 10.0;

        // generate one session per week; stop as soon as hours are covered (can end before deadline)
        double scheduled = 0;
        LocalDate weekStart = startDate;
        for (int i = 0; i < totalWeeks && scheduled < remaining - 0.001; i++) {
            double thisWeek = Math.round(Math.min(hoursPerWeek, remaining - scheduled) * 10.0) / 10.0;
            LocalDate sessionDate = weekStart.isAfter(item.getDeadline()) ? item.getDeadline() : weekStart;
            sessions.add(new StudySession(sessionDate, item.getTitle(), thisWeek));
            scheduled += thisWeek;
            weekStart = weekStart.plusWeeks(1);
        }
        return sessions;
    }

    @Override
    public String getStrategyName() {
        return "Weekly";
    }
}
