package studyplanner;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyPlanningStrategyTest {

    private final DailyPlanningStrategy strategy = new DailyPlanningStrategy();
    private final LocalDate today = LocalDate.now();

    @Test
    void testSessionsAreGeneratedForNormalInput() {
        StudyItem item = new StudyItem("Java", today.plusWeeks(2), 14, 7);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        assertFalse(sessions.isEmpty());
    }

    @Test
    void testCompletedItemProducesNoSessions() {
        StudyItem item = new StudyItem("Java", today.plusWeeks(2), 10, 5);
        item.addProgress(10);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void testTotalHoursApproximatesRemaining() {
        StudyItem item = new StudyItem("Calc", today.plusDays(7), 7, 7);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        double total = sessions.stream().mapToDouble(StudySession::getHoursPlanned).sum();
        assertEquals(7.0, total, 1.0);
    }

    @Test
    void testNoSessionExceedsDeadline() {
        LocalDate deadline = today.plusDays(10);
        StudyItem item = new StudyItem("History", deadline, 10, 5);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        sessions.forEach(s -> assertFalse(s.getDate().isAfter(deadline),
                "Session date " + s.getDate() + " is after deadline " + deadline));
    }

    @Test
    void testStrategyNameIsDaily() {
        assertEquals("Daily", strategy.getStrategyName());
    }

    @Test
    void testDaysPerWeekLimitIsRespected() {
        // 13-day span = exactly 2 full 7-day windows; 2 days/week → exactly 4 study days
        StudyItem item = new StudyItem("Bio", today.plusDays(13), 8, 2);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        assertEquals(4, sessions.size());
    }
}
