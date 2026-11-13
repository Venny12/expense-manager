package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.validation.InputValidator;
import com.revature.expensemanager.validation.InputValidator.DateRange;

/*
VR-07: Report date filter rules, cases TC-MGR-031 to TC-MGR-034.
Pure validation, so no database and no mocks are needed.
*/

@Tag("unit")
class DateFilterUnitTest {

    private static final String DATE_FORMAT_RULE = "Dates must be in YYYY-MM-DD format.";
    private static final String DATE_ORDER_RULE = "The start date must be on or before the end date.";

    // TC-MGR-031  Filter report by valid date range
    // the tests below only check that valid ranges are accepted

    // TC-MGR-032  Start date after end date
    static Stream<Arguments> reversedRanges() {
        return Stream.of(
            Arguments.of("TC-MGR-032-start-date-after-end-date", "2026-10-31", "2026-10-01")
        );
    }

    @Tag("negative")
    @DisplayName("TC-MGR-032 / VR-07: a start date after the end date is rejected")
    @ParameterizedTest(name = "{0}")
    @MethodSource("reversedRanges")
    void startDateAfterEndDateIsRejected(
        String caseId, String start, String end
    ) {
        assertFalse(InputValidator.isValidDateRange(start, end));
        ValidationException error = assertThrows(
            ValidationException.class,
            () -> InputValidator.parseDateRange(start, end)
        );
        assertEquals(DATE_ORDER_RULE, error.getMessage());
    }

    // TC-MGR-033  Start date equals end date

    @Tag("boundary")
    @DisplayName("TC-MGR-033 / VR-07: a start date equal to the end date is accepted")
    @Test
    void startDateEqualToEndDateIsAccepted() {
        assertTrue(InputValidator.isValidDateRange("2026-10-15", "2026-10-15"));
        DateRange range = InputValidator.parseDateRange("2026-10-15", "2026-10-15");
        assertEquals(LocalDate.of(2026, 10, 15), range.start());
        assertEquals(LocalDate.of(2026, 10, 15), range.end());
    }

    @Tag("boundary")
    @DisplayName("TC-MGR-031 / VR-07: a valid range is returned with its two dates, spaces around them ignored")
    @Test
    void validRangeIsReturnedWithBothDates() {
        DateRange range = InputValidator.parseDateRange(" 2026-10-01 ", "2026-10-31 ");
        assertEquals(LocalDate.of(2026, 10, 1), range.start());
        assertEquals(LocalDate.of(2026, 10, 31), range.end());
    }

    // TC-MGR-034  Invalid date format
    static Stream<Arguments> invalidDates() {
        return Stream.of(
            Arguments.of("text", "abc"),
            Arguments.of("empty", ""),
            Arguments.of("slashes", "2026/10/01"),
            Arguments.of("impossible-month", "2026-13-01"),
            Arguments.of("feb-30", "2026-02-30"),
            Arguments.of("not-a-leap-year", "2026-02-29")
        );
    }
    @Tag("negative")
    @DisplayName("TC-MGR-034 / VR-07: an invalid start date is rejected with the format note")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDates")
    void invalidStartDateIsRejected(String caseId, String date) {
        assertFalse(InputValidator.isValidDate(date));
        assertFalse(InputValidator.isValidDateRange(date, "2026-10-31"));
        ValidationException error = assertThrows(
            ValidationException.class,
            () -> InputValidator.parseDateRange(date, "2026-10-31")
        );
        assertEquals(DATE_FORMAT_RULE, error.getMessage());
    }

    @Tag("negative")
    @DisplayName("TC-MGR-034 / VR-07: an invalid end date is rejected with the format note")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDates")
    void invalidEndDateIsRejected(String caseId, String date) {
        assertFalse(InputValidator.isValidDateRange("2026-10-01", date));
        ValidationException error = assertThrows(
            ValidationException.class,
            () -> InputValidator.parseDateRange("2026-10-01", date)
        );
        assertEquals(DATE_FORMAT_RULE, error.getMessage());
    }

    static Stream<Arguments> validDates() {
        return Stream.of(
            Arguments.of("leap-day", "2028-02-29", LocalDate.of(2028, 2, 29)),
            Arguments.of("last-of-february", "2026-02-28", LocalDate.of(2026, 2, 28))
        );
    }

    @Tag("boundary")
    @DisplayName("TC-MGR-034 / VR-07: real dates, including a leap day, are accepted")
    @ParameterizedTest(name = "{0}")
    @MethodSource("validDates")
    void realDatesAreAccepted(
        String caseId, String date, LocalDate expected
    ) {
        assertTrue(InputValidator.isValidDate(date));
        DateRange range = InputValidator.parseDateRange(date, date);
        assertEquals(expected, range.start());
    }
}