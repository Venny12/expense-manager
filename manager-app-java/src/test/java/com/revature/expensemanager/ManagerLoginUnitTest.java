package com.revature.expensemanager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.revature.expensemanager.exceptions.AuthenticationException;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.DatabaseException;
import com.revature.expensemanager.exceptions.ValidationException;
import com.revature.expensemanager.services.AuthenticationService;

/*
FR-A1, FR-A2, FR-A4: manager login, cases TC-MGR-001 to TC-MGR-004 and TC-MGR-006 to TC-MGR-009.
 
The JDBC connection, statement and result set are mocked, so no database file is used.

TC-MGR-005 (three failed logins) is a manual case.
 */

@Tag("unit")
class ManagerLoginUnitTest {

    private static final String INVALID_LOGIN = "Invalid username or password.";
    private static final String REQUIRED_FIELDS = "Username and password are required.";
    private static final String NO_ACCESS = "This account does not have access to this app.";

    private Connection connection;
    private PreparedStatement statement;
    private ResultSet results;
    private AuthenticationService service;

    @BeforeEach
    void setUp() throws SQLException {
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        results = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);

        service = new AuthenticationService(connection);
    }

    // The database holds one account with this role and no other.
    private void accountExists(String role) throws SQLException {
        when(results.next()).thenReturn(true);
        when(results.getInt("id")).thenReturn(10);
        when(results.getString("username")).thenReturn("manager1");
        when(results.getString("role")).thenReturn(role);
        when(results.getString("full_name")).thenReturn("Test Manager");
    }

    // No account matches the username and password pair.
    private void noMatchingAccount() throws SQLException {
        when(results.next()).thenReturn(false);
    }

    // TC-MGR-001

    @DisplayName("TC-MGR-001 / FR-A1: valid manager credentials return the manager")
    @Test
    void validManagerCredentialsReturnTheManagerAccount() throws SQLException {
        accountExists("manager");

        User user = service.login("manager1", "ManagerPass1!");

        assertEquals(10, user.id());
        assertEquals("manager1", user.username());
        assertEquals("manager", user.role());
        assertEquals("Test Manager", user.fullName());
    }

    @DisplayName("TC-MGR-001 / FR-S2: credentials are bound as parameters, not joined into SQL")
    @Test
    void credentialsAreSentToTheDatabaseAsParameters() throws SQLException {
        accountExists("manager");
        service.login("manager1", "ManagerPass1!");
        verify(statement).setString(1, "manager1");
        verify(statement).setString(2, "ManagerPass1!");
        verify(connection).prepareStatement(contains("WHERE username = ? AND password = ?"));
    }

    // TC-MGR-002, TC-MGR-003, TC-MGR-004
    static Stream<Arguments> credentialsWithoutAMatch() {
        return Stream.of(
            Arguments.of(
                "TC-MGR-002-unknown-username-wrong-password",
                "nobody", "WrongPass1!"
            ),
            Arguments.of(
                "TC-MGR-003-valid-username-wrong-password",
                "manager1", "WrongPass1!"
            ),
            Arguments.of(
                "TC-MGR-004-unknown-username-valid-password",
                "nobody", "ManagerPass1!"
            )
        );
    }

    @Tag("negative")
    @DisplayName("FR-A1: credentials that match no account are refused with one message")
    @ParameterizedTest(name = "{0}")
    @MethodSource("credentialsWithoutAMatch")
    void credentialsWithoutAMatchAreRefused(
        String caseId, String username, String password
    ) throws SQLException {
        noMatchingAccount();

        AuthenticationException error = assertThrows(
            AuthenticationException.class,
            () -> service.login(username, password)
        );

        // The same message for every case: it does not say which part was wrong.
        assertEquals(INVALID_LOGIN, error.getMessage());
        verify(statement).setString(1, username);
        verify(statement).setString(2, password);
    }

    // TC-MGR-006, TC-MGR-007, TC-MGR-008
    static Stream<Arguments> missingCredentials() {
        return Stream.of(
            Arguments.of("TC-MGR-006-both-empty", "", ""),
            Arguments.of("TC-MGR-006-both-null", null, null),
            Arguments.of("TC-MGR-006-both-spaces", "   ", "   "),
            Arguments.of("TC-MGR-007-username-empty", "", "ManagerPass1!"),
            Arguments.of("TC-MGR-007-username-null", null, "ManagerPass1!"),
            Arguments.of("TC-MGR-007-username-spaces", "   ", "ManagerPass1!"),
            Arguments.of("TC-MGR-008-password-empty", "manager1", ""),
            Arguments.of("TC-MGR-008-password-null", "manager1", null),
            Arguments.of("TC-MGR-008-password-spaces", "manager1", "   ")
        );
    }

    @Tag("negative")
    @DisplayName("FR-A1 / VR-01: missing credentials are rejected before any database lookup")
    @ParameterizedTest(name = "{0}")
    @MethodSource("missingCredentials")
    void missingCredentialsAreRejectedWithoutALookup(
        String caseId, String username, String password
    ) {
        ValidationException error = assertThrows(
            ValidationException.class,
            () -> service.login(username, password)
        );

        assertEquals(REQUIRED_FIELDS, error.getMessage());
        verifyNoInteractions(connection);
    }

    // TC-MGR-009
    @Tag("negative")
    @DisplayName("TC-MGR-009 / FR-A4: an employee account is refused by the Manager App")
    @Test
    void employeeAccountIsRefusedWithAnAccessMessage() throws SQLException {
        accountExists("employee");
        AuthorizationException error = assertThrows(
            AuthorizationException.class,
            () -> service.login("employee1", "EmployeePass1!")
        );
        assertEquals(NO_ACCESS, error.getMessage());
    }

    @Tag("negative")
    @DisplayName("TC-MGR-009 / FR-A4: only the manager role gets access, whatever else is stored")
    @ParameterizedTest(name = "role: {0}")
    @MethodSource("rolesThatAreNotManager")
    void everyRoleExceptManagerIsRefused(String role) throws SQLException {
        accountExists(role);
        assertThrows(
            AuthorizationException.class,
            () -> service.login("someone", "SomePass1!")
        );
    }
    static Stream<Arguments> rolesThatAreNotManager() {
        return Stream.of(
            Arguments.of("employee"),
            Arguments.of("Manager"),
            Arguments.of("MANAGER"),
            Arguments.of(""),
            Arguments.of((String) null)
        );
    }

    // Related check
    @Tag("negative")
    @DisplayName("FR-A1: a database failure during login gives a clear error")
    @Test
    void databaseFailureDuringLoginGivesAClearError() throws SQLException {
        when(statement.executeQuery()).thenThrow(new SQLException("disk I/O error"));
        DatabaseException error = assertThrows(
            DatabaseException.class,
            () -> service.login("manager1", "ManagerPass1!")
        );
        assertEquals("Could not check login details.", error.getMessage());
    }
}