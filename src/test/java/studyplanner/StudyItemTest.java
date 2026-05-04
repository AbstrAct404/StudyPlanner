package studyplanner;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StudyItemTest {

    private final LocalDate futureDate = LocalDate.now().plusDays(14);

    @Test
    void testCreationSetsFieldsCorrectly() {
        StudyItem item = new StudyItem("Math", futureDate, 20, 5);
        assertEquals("Math", item.getTitle());
        assertEquals(futureDate, item.getDeadline());
        assertEquals(20, item.getTotalHours(), 0.001);
        assertEquals(0, item.getHoursCompleted(), 0.001);
        assertEquals(5, item.getDaysAvailablePerWeek());
    }

    @Test
    void testRemainingHoursAfterProgress() {
        StudyItem item = new StudyItem("Math", futureDate, 20, 5);
        item.addProgress(5);
        assertEquals(15, item.getRemainingHours(), 0.001);
    }

    @Test
    void testIsCompleteWhenAllHoursDone() {
        StudyItem item = new StudyItem("Math", futureDate, 10, 5);
        assertFalse(item.isComplete());
        item.addProgress(10);
        assertTrue(item.isComplete());
    }

    @Test
    void testProgressCappedAtTotalHours() {
        StudyItem item = new StudyItem("Math", futureDate, 10, 5);
        item.addProgress(99);
        assertEquals(10, item.getHoursCompleted(), 0.001);
        assertEquals(0, item.getRemainingHours(), 0.001);
    }

    @Test
    void testNegativeProgressThrowsException() {
        StudyItem item = new StudyItem("Math", futureDate, 10, 5);
        assertThrows(IllegalArgumentException.class, () -> item.addProgress(-1));
    }

    @Test
    void testIdIsUniquePerInstance() {
        StudyItem a = new StudyItem("A", futureDate, 5, 3);
        StudyItem b = new StudyItem("B", futureDate, 5, 3);
        assertNotEquals(a.getId(), b.getId());
    }
}
