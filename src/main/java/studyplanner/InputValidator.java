package studyplanner;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class InputValidator {

    private InputValidator() {}

    public static String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty.");
        }
        return title.trim();
    }

    public static double validateHours(double hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Hours must be greater than zero.");
        }
        if (hours > 10000) {
            throw new IllegalArgumentException("Hours value is unrealistically large (max 10000).");
        }
        return hours;
    }

    public static LocalDate validateDeadline(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Deadline cannot be empty.");
        }
        try {
            LocalDate date = LocalDate.parse(dateStr.trim());
            if (date.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Deadline cannot be in the past.");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD (e.g. 2026-05-15).");
        }
    }

    public static int validateDaysPerWeek(int days) {
        if (days < 1 || days > 7) {
            throw new IllegalArgumentException("Days per week must be between 1 and 7.");
        }
        return days;
    }

    public static double validateProgressHours(double hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Progress hours must be greater than zero.");
        }
        return hours;
    }
}
