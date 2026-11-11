# Expense Manager – Test Plan

**Project:** Expense Manager

## 1. Objective

The objective of this testing is to make sure that the Expense Manager application works correctly and that employees and managers can perform their tasks without errors.

We will test both applications and make sure they work properly with the same SQLite database. We will also check that expense information is saved, updated, and displayed correctly across both applications.

## 2. Scope

Testing will cover:

- Python Employee Application
- Java Manager Application
- Shared SQLite database
- Communication between both applications through the database

### 2.1 Features to Test

#### Employee Application (Python)

- Employee login and authentication.
- Submitting new expenses.
- Viewing personal expenses and their status.
- Editing and deleting pending expenses.
- Viewing approved and denied expenses with manager comments.
- Input validation and error messages.

#### Manager Application (Java)

- Manager login and authentication.
- Viewing all pending expenses.
- Approving or denying expenses with comments.
- Generating reports by employee and category.
- Filtering reports by date range.
- Exporting reports to CSV.

#### Database and Shared Functionality

- Database creation and table constraints.
- Correct storage and retrieval of expense information.
- Expense status updates.
- Employee and manager access permissions.
- Logging and error handling.
- Consistent data between both applications.

### 2.2 Exclusions

The following items are outside the scope of this project:

- Web or graphical user interface testing.
- Browser automation.
- API testing.
- Performance and load testing.
- Production-level security testing.
- Expense reversal after a manager's decision.
- Receipt uploads and multiple currency support.
- Bulk data imports.

## 3. Test Strategy

We will use a combination of automated and manual testing.

### 3.1 Unit Testing

- Use PyTest to test individual functions and business rules in the Python application.
- Use JUnit 5 to test individual methods and business rules in the Java application.
- Use Mockito to test specific scenarios without depending on actual database operations.

### 3.2 Integration Testing

- Check whether application components work correctly with the SQLite database.
- Use a separate temporary database for each automated integration test.
- Verify that database operations save and retrieve the correct information.

### 3.3 SQL Validation Testing

- Run five SQL validation queries through PyTest.
- Check for missing expense records, incorrect approval records, invalid statuses, and other database issues.
- Each query should return no rows when the data is correct.

### 3.4 Manual Testing

- Check the login screens and menu flows of both applications.
- Verify that tables and reports are displayed correctly.
- Open exported CSV files in a spreadsheet to check their contents.
- Run all six cross-application test scenarios with both applications using the same database.

### 3.5 Exploratory Testing

- Conduct two exploratory testing sessions of approximately 30 minutes each.
- One session will focus on the Employee Application and the other on the Manager Application.
- Test unusual inputs and situations that may not be covered by existing test cases.

### 3.6 Test Design Techniques

We will use:

- Equivalence Partitioning.
- Boundary Value Analysis.
- Decision Table Testing.
- State Transition Testing.
- Error Guessing.

## 4. Test Coverage

Our team has designed 40 test cases.

- 30 automated test cases (75%).
- 10 manual test cases (25%), including six cross-application scenarios.
- At least 40% of test cases must cover invalid inputs or boundary conditions. Our current design includes 24 such cases (60%).

### 4.1 Minimum Testing Requirements

We will also meet the minimum testing requirements.

**Python:**

- At least 20 unit tests.
- At least 5 integration tests.
- 5 SQL validation queries.
- At least 1 menu-flow test.

**Java:**

- At least 20 unit tests.
- At least 4 integration tests.
- At least 1 menu-flow test.

We will also use fixtures, parameterized tests, mocking, custom exception testing, and HTML reports as required by the project.

## 5. Resources

### 5.1 Team

- **Team members:** 2 team members – Vennela Preethi and Shaqayeq.
- Both members will contribute to development and testing in Python and Java.
- Vennela will lead defect tracking and the final test summary.
- Shaqayeq will lead the requirements checklist.
- Both members will review the Requirements Traceability Matrix (RTM) and perform cross-application testing.

### 5.2 Tools

- Python and PyTest.
- Java and JUnit 5.
- Mockito for Java testing.
- SQLite for database testing.
- Maven for building and running Java tests.
- GitHub for version control and code reviews.
- Jira for tracking tasks and defects.
- Shared CSV test data for validation testing.

## 6. Test Environment

- A Windows development environment with the required Python and Java versions.
- Python Employee Application and Java Manager Application.
- SQLite database shared by both applications.
- A separate temporary database for automated tests.
- Sample employee and manager accounts.
- Shared validation test data in CSV format.
- Required Python packages and Maven dependencies.

Both applications must use the correct `EM_DB_PATH` when performing manual cross-application testing.

## 7. Risks

The main risks we may face are:

1. **Database mismatch:** Both applications may accidentally connect to different database files, causing inconsistent information.
2. **Access control issues:** Employees may be able to view or modify expenses belonging to other employees.
3. **Expense status issues:** An employee may try to edit or delete an expense after a manager has approved or denied it.
4. **Data inconsistency:** Expense amounts, dates, or report totals may appear differently in the two applications.
5. **Database errors:** A failed operation may leave incomplete or incorrect records.
6. **Time limitations:** Completing the required automated tests, documentation, and defect fixes may take longer than expected.

We will reduce these risks by using temporary databases, shared test data, regular code reviews, and cross-application testing.

## 8. Test Timeline

### Phase 1 – Test Preparation

- Review project requirements.
- Prepare the test plan and test cases.
- Set up the test environment and sample data.
- Prepare the Requirements Traceability Matrix.

### Phase 2 – Automated Testing

- Write and execute Python unit and integration tests.
- Write and execute Java unit and integration tests.
- Complete SQL validation tests.
- Generate automated test reports.

### Phase 3 – Manual and Exploratory Testing

- Test login and application menu flows.
- Perform all six cross-application test scenarios.
- Conduct exploratory testing sessions.
- Participate in the peer bug bash.

### Phase 4 – Defect Tracking and Retesting

- Report defects in Jira.
- Fix and retest identified defects.
- Perform regression testing.
- Run the second test cycle.

### Phase 5 – Final Review

- Complete the RTM.
- Review test results and evidence.
- Complete the AI test review and test effectiveness documents.
- Prepare the final test summary and go/no-go recommendation.

## 9. Deliverables

- Test plan.
- Designed test cases.
- Requirements Traceability Matrix (RTM).
- Automated PyTest and JUnit test suites.
- SQL validation queries.
- PyTest and Surefire HTML reports.
- Defect log in Jira.
- Exploratory testing notes.
- AI test review log.
- Test effectiveness report.
- Final test summary report with test results and recommendations.

## 10. Success Criteria

Testing will be considered complete when:

- Every Must functional requirement has at least one designed test case and one automated test linked in the RTM.
- All required unit, integration, SQL, and menu-flow testing minimums are met.
- All automated tests pass on a clean clone, except permitted skips linked to open Jira defects.
- All six cross-application scenarios are completed in both test cycles.
- No Critical or High severity defects remain open.
- All required documents and test evidence are completed.
- Two test cycles are recorded.
- The final test summary includes known issues and a go/no-go recommendation.

## 11. Documentation and References

The following documents will be used during testing:

- Project Requirements Document.
- User Stories and Acceptance Criteria.
- Database Schema and SQL Validation Queries.
- Test Cases.
- Requirements Traceability Matrix.
- Defect Records in Jira.

