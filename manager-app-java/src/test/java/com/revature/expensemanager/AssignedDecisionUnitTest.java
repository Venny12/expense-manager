package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.revature.expensemanager.dao.ExpenseDao;
import com.revature.expensemanager.exceptions.InvalidStateException;
import com.revature.expensemanager.exceptions.NotFoundException;
import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.services.ExpenseService;

@Tag("unit")
class AssignedDecisionUnitTest {

    private ExpenseDao dao;
    private ExpenseService service;
    private User manager;

    private static final String REVIEW_DATE = "2026-11-10";
    private static final String DENIAL_RULE =
        "A denial needs a comment of 5 to 200 characters.";

    @BeforeEach
    void setUp() {
        dao = mock(ExpenseDao.class);

        Clock clock = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"),
            ZoneOffset.UTC
        );

        service = new ExpenseService(dao, clock);
        manager = new User(10, "manager1", "manager", "Test Manager");
    }

    static Stream<Arguments> invalidComments() {
        return Stream.of(
            Arguments.of("TC-MGR-019-empty", ""),
            Arguments.of("TC-MGR-020-four-characters", "abcd"),
            Arguments.of("TC-MGR-021-201-characters", "a".repeat(201)),
            Arguments.of("TC-MGR-022-spaces", "     "),
            Arguments.of("TC-MGR-019-null", (String) null)
        );
    }

    @Tag("negative")
    @DisplayName("VR-05: invalid denial comments cannot reach the DAO")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidComments")
    void invalidDenialCommentDoesNotSave(String caseId, String comment) {
        ValidationException error = assertThrows(
            ValidationException.class,
            () -> service.deny(manager, 1, comment)
        );

        assertEquals(DENIAL_RULE, error.getMessage());
        verifyNoInteractions(dao);
    }

    static Stream<Arguments> validComments() {
        return Stream.of(
            Arguments.of(
                "TC-MGR-018-minimum-five", "abcde", "abcde"
            ),
            Arguments.of(
                "TC-MGR-021-maximum-200",
                "a".repeat(200),
                "a".repeat(200)
            ),
            Arguments.of(
                "TC-MGR-018-trimming",
                "  Outside policy  ",
                "Outside policy"
            )
        );
    }

    @Tag("boundary")
    @DisplayName("FR-M2 / VR-05: valid denial comments are cleaned and saved")
    @ParameterizedTest(name = "{0}")
    @MethodSource("validComments")
    void validDenialSavesExpectedDecision(
        String caseId, String comment, String expected
    ) throws Exception {
        when(dao.findStatus(1)).thenReturn("pending");
        when(dao.saveDecision(
            1, "denied", manager.id(), expected, REVIEW_DATE
        )).thenReturn(true);

        service.deny(manager, 1, comment);

        verify(dao).findStatus(1);
        verify(dao).saveDecision(
            1, "denied", manager.id(), expected, REVIEW_DATE
        );
        verifyNoMoreInteractions(dao);
    }

    static Stream<Arguments> decidedExpenses() {
        return Stream.of(
            Arguments.of("approved", true),
            Arguments.of("denied", false)
        );
    }

    @Tag("negative")
    @DisplayName("FR-M2 / TC-MGR-023: refuse changes to a decided expense")
    @ParameterizedTest(name = "Status: {0}; attempt denial: {1}")
    @MethodSource("decidedExpenses")
    void decidedExpenseCannotBeChanged(
        String status, boolean deny
    ) throws Exception {
        when(dao.findStatus(1)).thenReturn(status);

        InvalidStateException error = assertThrows(
            InvalidStateException.class,
            () -> {
                if (deny) {
                    service.deny(manager, 1, "Outside policy");
                } else {
                    service.approve(manager, 1, "Approved");
                }
            }
        );

        assertEquals(
            "Expense is no longer pending.", error.getMessage()
        );

        verify(dao).findStatus(1);
        verifyNoMoreInteractions(dao);
    }

    @Test
    @Tag("negative")
    @DisplayName("FR-M2 / TC-MGR-024: missing expense fails the precheck")
    void missingExpenseIsRejectedBeforeDecision() throws Exception {
        when(dao.findStatus(999)).thenReturn(null);

        NotFoundException error = assertThrows(
            NotFoundException.class,
            () -> service.requirePending(manager, 999)
        );

        assertEquals("Expense not found.", error.getMessage());
        verify(dao).findStatus(999);
        verifyNoMoreInteractions(dao);
    }

    @Test
    @Tag("negative")
    @DisplayName("FR-M2 / TC-MGR-026: detect a decision made after lookup")
    void decisionChangedDuringSaveIsRejected() throws Exception {
        when(dao.findStatus(1)).thenReturn("pending");

        // Another manager has decided it before our update runs.
        when(dao.saveDecision(
            1, "denied", manager.id(), "Outside policy", REVIEW_DATE
        )).thenReturn(false);

        InvalidStateException error = assertThrows(
            InvalidStateException.class,
            () -> service.deny(manager, 1, "Outside policy")
        );

        assertEquals(
            "Expense is no longer pending.", error.getMessage()
        );

        verify(dao).findStatus(1);
        verify(dao).saveDecision(
            1, "denied", manager.id(), "Outside policy", REVIEW_DATE
        );
        verifyNoMoreInteractions(dao);
    }
}