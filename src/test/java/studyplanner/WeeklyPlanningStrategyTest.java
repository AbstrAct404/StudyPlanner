package studyplanner;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyPlanningStrategyTest {

    private final WeeklyPlanningStrategy strategy = new WeeklyPlanningStrategy();
    private final LocalDate today = LocalDate.now();

    @Test
    void testMultipleWeeksProduceMultipleSessions() {
        StudyItem item = new StudyItem("Physics", today.plusWeeks(4), 40, 5);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        assertTrue(sessions.size() >= 4);
    }

    @Test
    void testCompletedItemProducesNoSessions() {
        StudyItem item = new StudyItem("Chemistry", today.plusWeeks(3), 20, 5);
        item.addProgress(20);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void testHoursDistributedEvenly() {
        StudyItem item = new StudyItem("Bio", today.plusWeeks(4), 40, 5);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        if (sessions.size() > 1) {
            double first = sessions.get(0).getHoursPlanned();
            double last  = sessions.get(sessions.size() - 1).getHoursPlanned();
            assertEquals(first, last, 0.2);
        }
    }

    @Test
    void testStrategyNameIsWeekly() {
        assertEquals("Weekly", strategy.getStrategyName());
    }

    @Test
    void testSingleDayDeadlineProducesOneSession() {
        StudyItem item = new StudyItem("Vocab", today.plusDays(1), 2, 7);
        List<StudySession> sessions = strategy.generateSessions(item, today);
        assertEquals(1, sessions.size());
    }
}
