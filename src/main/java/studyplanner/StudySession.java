package studyplanner;

import java.time.LocalDate;

public class StudySession {

    private final LocalDate date;
    private final String studyItemTitle;
    private final double hoursPlanned;

    public StudySession(LocalDate date, String studyItemTitle, double hoursPlanned) {
        this.date = date;
        this.studyItemTitle = studyItemTitle;
        this.hoursPlanned = hoursPlanned;
    }

    public LocalDate getDate() { return date; }
    public String getStudyItemTitle() { return studyItemTitle; }
    public double getHoursPlanned() { return hoursPlanned; }

    @Override
    public String toString() {
        return String.format("  %s  %.1fh  %s", date, hoursPlanned, studyItemTitle);
    }
}
