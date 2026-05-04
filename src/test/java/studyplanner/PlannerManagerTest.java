package studyplanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class PlannerManagerTest {

    private PlannerManager manager;
    private final LocalDate future = LocalDate.now().plusDays(14);

    @BeforeEach
    void setUp() {
        manager = new PlannerManager();
    }

    @Test
    void testAddAndRetrieveItem() {
        StudyItem item = new StudyItem("Math", future, 20, 5);
        manager.addStudyItem(item);
        assertEquals(item, manager.getStudyItem(item.getId()));
    }

    @Test
    void testRemoveItem() {
        StudyItem item = new StudyItem("Math", future, 20, 5);
        manager.addStudyItem(item);
        assertTrue(manager.removeStudyItem(item.getId()));
        assertThrows(NoSuchElementException.class, () -> manager.getStudyItem(item.getId()));
    }

    @Test
    void testRemoveNonExistentReturnsFalse() {
        assertFalse(manager.removeStudyItem("does-not-exist"));
    }

    @Test
    void testRecordProgressUpdatesItem() {
        StudyItem item = new StudyItem("Math", future, 20, 5);
        manager.addStudyItem(item);
        manager.recordProgress(item.getId(), 5);
        assertEquals(5, item.getHoursCompleted(), 0.001);
    }

    @Test
    void testGeneratePlanReturnsPlan() {
        StudyItem item = new StudyItem("Physics", future, 20, 5);
        manager.addStudyItem(item);
        StudyPlan plan = manager.generatePlan(item.getId(), new DailyPlanningStrategy());
        assertNotNull(plan);
        assertFalse(plan.getSessions().isEmpty());
    }

    @Test
    void testGetItemThrowsWhenNotFound() {
        assertThrows(NoSuchElementException.class, () -> manager.getStudyItem("ghost"));
    }

    @Test
    void testAddNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> manager.addStudyItem(null));
    }

    @Test
    void testGetAllItemsReflectsAdditions() {
        manager.addStudyItem(new StudyItem("A", future, 5, 3));
        manager.addStudyItem(new StudyItem("B", future, 5, 3));
        assertEquals(2, manager.getAllItems().size());
    }

    // ── stream-method tests (Lec3) ────────────────────────────────────────────

    @Test
    void getItemsSortedByDeadlineOrdersCorrectly() {
        StudyItem near = new StudyItem("Near", LocalDate.now().plusDays(3),  5, 3);
        StudyItem far  = new StudyItem("Far",  LocalDate.now().plusDays(20), 5, 3);
        manager.addStudyItem(far);   // added out-of-order on purpose
        manager.addStudyItem(near);
        List<StudyItem> sorted = manager.getItemsSortedByDeadline();
        assertEquals("Near", sorted.get(0).getTitle());
        assertEquals("Far",  sorted.get(1).getTitle());
    }

    @Test
    void getIncompleteItemsExcludesFinishedItems() {
        StudyItem done    = new StudyItem("Done",    future, 10, 5);
        StudyItem pending = new StudyItem("Pending", future, 10, 5);
        done.addProgress(10);
        manager.addStudyItem(done);
        manager.addStudyItem(pending);
        List<StudyItem> incomplete = manager.getIncompleteItems();
        assertEquals(1, incomplete.size());
        assertEquals("Pending", incomplete.get(0).getTitle());
    }

    @Test
    void getTotalRemainingHoursSumsCorrectly() {
        StudyItem a = new StudyItem("A", future, 10, 5);
        StudyItem b = new StudyItem("B", future, 20, 5);
        a.addProgress(3);
        manager.addStudyItem(a);
        manager.addStudyItem(b);
        assertEquals(27.0, manager.getTotalRemainingHours(), 0.001);
    }

    @Test
    void getTotalRemainingHoursIsZeroWhenAllComplete() {
        StudyItem item = new StudyItem("Done", future, 10, 5);
        item.addProgress(10);
        manager.addStudyItem(item);
        assertEquals(0.0, manager.getTotalRemainingHours(), 0.001);
    }
}
