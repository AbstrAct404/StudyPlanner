package studyplanner;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    @Test
    void testValidTitleReturned() {
        assertEquals("Math", InputValidator.validateTitle("  Math  "));
    }

    @Test
    void testEmptyTitleThrows() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateTitle(""));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateTitle("   "));
    }

    @Test
    void testNullTitleThrows() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateTitle(null));
    }

    @Test
    void testValidHoursReturned() {
        assertEquals(10.0, InputValidator.validateHours(10.0), 0.001);
    }

    @Test
    void testZeroOrNegativeHoursThrow() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateHours(0));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateHours(-5));
    }

    @Test
    void testExcessiveHoursThrow() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateHours(99999));
    }

    @Test
    void testValidDeadlineParsed() {
        String future = LocalDate.now().plusDays(5).toString();
        assertEquals(LocalDate.now().plusDays(5), InputValidator.validateDeadline(future));
    }

    @Test
    void testPastDeadlineThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateDeadline("2020-01-01"));
    }

    @Test
    void testBadDateFormatThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateDeadline("05/15/2027"));
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateDeadline("not-a-date"));
    }

    @Test
    void testValidDaysPerWeek() {
        assertEquals(5, InputValidator.validateDaysPerWeek(5));
    }

    @Test
    void testInvalidDaysPerWeekThrows() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateDaysPerWeek(0));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateDaysPerWeek(8));
    }

    @Test
    void testValidProgressHours() {
        assertEquals(2.5, InputValidator.validateProgressHours(2.5), 0.001);
    }

    @Test
    void testZeroProgressHoursThrow() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateProgressHours(0));
    }
}
