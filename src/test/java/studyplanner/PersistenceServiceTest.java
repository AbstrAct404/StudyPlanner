package studyplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceServiceTest {

    @TempDir
    Path tempDir;

    private final LocalDate future = LocalDate.now().plusDays(14);

    @Test
    void loadFromNonExistentFileReturnsEmpty() {
        PersistenceService ps = new PersistenceService(tempDir.resolve("missing.json"));
        assertTrue(ps.load().isEmpty());
    }

    @Test
    void saveAndLoadRoundTrip() {
        PersistenceService ps = new PersistenceService(tempDir.resolve("data.json"));

        StudyItem item = new StudyItem("Math", future, 20, 5);
        item.addProgress(5);

        ps.save(List.of(item));
        List<StudyItem> loaded = ps.load();

        assertEquals(1, loaded.size());
        StudyItem r = loaded.get(0);
        assertEquals("Math", r.getTitle());
        assertEquals(future, r.getDeadline());
        assertEquals(20, r.getTotalHours(), 0.001);
        assertEquals(5,  r.getHoursCompleted(), 0.001);
        assertEquals(5,  r.getDaysAvailablePerWeek());
    }

    @Test
    void saveEmptyListProducesEmptyLoadResult() {
        PersistenceService ps = new PersistenceService(tempDir.resolve("empty.json"));
        ps.save(List.of());
        assertTrue(ps.load().isEmpty());
    }

    @Test
    void multipleItemsRoundTrip() {
        PersistenceService ps = new PersistenceService(tempDir.resolve("multi.json"));

        StudyItem a = new StudyItem("Physics",   future,            10, 3);
        StudyItem b = new StudyItem("Chemistry", future.plusDays(7), 15, 4);

        ps.save(List.of(a, b));
        List<StudyItem> loaded = ps.load();

        assertEquals(2, loaded.size());
        assertEquals("Physics",   loaded.get(0).getTitle());
        assertEquals("Chemistry", loaded.get(1).getTitle());
        assertEquals(10, loaded.get(0).getTotalHours(), 0.001);
        assertEquals(15, loaded.get(1).getTotalHours(), 0.001);
    }

    @Test
    void titleWithSpecialCharactersRoundTrips() {
        PersistenceService ps = new PersistenceService(tempDir.resolve("special.json"));

        StudyItem item = new StudyItem("CS & OOP", future, 8, 5);
        ps.save(List.of(item));

        assertEquals("CS & OOP", ps.load().get(0).getTitle());
    }

    @Test
    void progressIsPreservedAcrossSaveLoad() {
        PersistenceService ps = new PersistenceService(tempDir.resolve("progress.json"));

        StudyItem item = new StudyItem("History", future, 30, 3);
        item.addProgress(12.5);

        ps.save(List.of(item));
        StudyItem loaded = ps.load().get(0);

        assertEquals(12.5, loaded.getHoursCompleted(), 0.001);
        assertEquals(17.5, loaded.getRemainingHours(), 0.001);
    }
}
