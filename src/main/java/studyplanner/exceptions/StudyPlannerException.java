package studyplanner.exceptions;

public class StudyPlannerException extends RuntimeException {

    public StudyPlannerException(String message) {
        super(message);
    }

    public StudyPlannerException(String message, Throwable cause) {
        super(message, cause);
    }
}
