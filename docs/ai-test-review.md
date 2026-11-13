# AI-Assisted Test Review Log

This log follows Section 9.8 (AI-Assisted Test Design). It documents test cases that were drafted or shaped with an AI assistant. Every suggestion was checked against the requirement, the validation rule, and the exact message before it was kept.

## Project Information

| **Item** | **Value** |
|---|---|
| Project | Expense Manager (Python Employee App, Java Manager App) |
| Authors | Vennela Preethi, Shaqayeq Salimy |
| Data shared with the AI tool | Test case document and project sample data only. No real passwords or personal data. |
| Last updated | 2026-11-12 |
| Prompt | I wrote these test cases myself. Please review them against the requirements, sample data (attached) and list the test cases I am missing, if there are any. For each missing case give: a proposed scenario, the requirement or validation rule ID it covers. Do not repeat cases I already have. |


# 1. AI-Assisted Test Cases

## 1.1 Implemented (Kept) – Python (PyTest)

| **Case ID** | **Scenario** | **Test (file :: function)** | **Owner** | **Why kept** |
|---|---|---|---|---|
| TC-EMP-010 | Two failures then success | `unit/test_login_screen.py :: test_two_failed_logins_then_success` | Shaqayeq | Boundary of the 3-attempt rule: the third attempt must still succeed and no lockout message may appear. |
| TC-EMP-011 | Password never written to the log | `integration/test_login_logging.py :: test_passwords_are_not_logged_or_printed` | Shaqayeq | Security requirement. It reads the real log file, so it catches a leak the unit tests cannot. |
| TC-EMP-027 | Expense and pending approval saved together | `integration/test_expense_submission.py :: test_expense_and_pending_approval_are_saved_together` | Shaqayeq | Checks both rows and the NULL review fields, not only that a save happened. |
| TC-EMP-028 | Database failure during save | `integration/test_expense_submission.py :: test_approval_failure_rolls_back_and_shows_friendly_error` | Shaqayeq | Proves all-or-nothing: a failure on the second insert leaves no expense row. |
| TC-EMP-029 | Special characters in description | `integration/test_expense_submission.py :: test_special_description_is_stored_as_data` | Shaqayeq | SQL-looking text must be stored as data, and the table must survive. |
| TC-EMP-040 | Invalid new value during edit | `unit/test_edit_expense.py :: test_edit_with_an_invalid_value_writes_nothing` and the four `test_edit_asks_again_...` tests | Vennela | Checks the stored record is unchanged and the app re-prompts. |
| TC-EMP-051 | Missing database file | `integration/test_app_error_handling.py :: test_missing_database_file_gives_friendly_message` | Vennela | Friendly message, no traceback, exit code 1 (FR-S4). |
| TC-EMP-053 | Error in one menu option does not end the session | `integration/test_app_error_handling.py :: test_error_in_one_menu_option_does_not_end_the_session` | Vennela | The session must survive an error and View must still work afterwards. |
| TC-EMP-055 | Unexpected error while the application runs | `integration/test_app_error_handling.py :: test_unexpected_error_while_running_is_logged_with_exit_code_1` | Vennela | Checks the screen message, the log entry and exit code 1 together. |
| TC-DB-004 | Seed script loads expected rows (PyTest + JUnit 5) | `integration/test_database_contract.py :: test_seed_script_loads_expected_rows`; Java `DatabaseContractIntegrationTest :: seedScriptLoadsExpectedRows` | Vennela | Expected row counts are written in the test, not derived from the seed script, so missing or extra rows are detected. It runs in both PyTest and JUnit 5 so both apps start from the same data. |

## 1.2 Implemented (Kept) – Java (JUnit 5)

| **Case ID** | **Scenario** | **Test (file :: method)** | **Owner** | **Why kept** |
|---|---|---|---|---|
| TC-MGR-010 | Password never written to the log | `LoggingIntegrationTest :: wrongPasswordIsNeverLoggedOrPrinted` (and four related login-failure variants) | Vennela | Security requirement. |
| TC-MGR-017 | Expense deleted by the employee before the decision | `ApproveExpenseTest :: expenseDeletedAfterListingIsNotFound` | Vennela | The race the cross-app case XAPP-004 checks by hand, automated at the Java level. |
| TC-MGR-022 | Denial comment of only spaces | `AssignedDecisionUnitTest :: invalidDenialCommentDoesNotSave`, row `TC-MGR-022-spaces` | Shaqayeq | Spaces must be trimmed and rejected, and the DAO must never be called. |
| TC-MGR-023 | Deny or approve an already decided expense | `AssignedDecisionUnitTest :: decidedExpenseCannotBeChanged`; `AssignedManagerIntegrationTest :: cannotChangeDecidedExpense` | Shaqayeq | Both decisions are refused with "already decided" and stored data is unchanged. |
| TC-MGR-026 | Two decisions on the same expense | `AssignedDecisionUnitTest :: decisionChangedDuringSaveIsRejected`; `AssignedManagerIntegrationTest :: secondDecisionKeepsFirstDecision` | Shaqayeq | The first decision and comment must survive a second attempt. |
| TC-MGR-033 | Start date equals end date | `DateFilterUnitTest :: startDateEqualToEndDateIsAccepted`; `ReportIntegrationTest` (one-day range tests) | Vennela | Boundary of VR-07: a one-day range must be valid and count that day only. |
| TC-MGR-038 | Missing database file | `ErrorHandlingIntegrationTest :: missingDatabaseFileGivesFriendlyMessage` | Vennela | Friendly message, no stack trace, exit code 1. |
| TC-MGR-039 | Scripted session: login, pending list, exit | `ManagerMenuFlowIntegrationTest :: scriptedSessionLoginPendingListExit` | Vennela | Only test that runs the real Main, login, menu and pending list together. It also checks the exact prompts and that the password is not on screen. |
| TC-MGR-041 | Error in one menu option does not end the session | `ErrorHandlingIntegrationTest :: errorInOneMenuOptionDoesNotEndTheSession` | Vennela | Same rule as TC-EMP-053, for the Java menu. |
| TC-MGR-043 | Unexpected error while the application runs | `ErrorHandlingIntegrationTest :: unexpectedErrorWhileRunningIsLoggedWithExitCode1` | Vennela | Message, log entry and exit code 1. The simulated failure was changed (see review entry 5). |

## 1.3 AI Suggestions Not Implemented

| **Case ID** | **Scenario** | **Decision** | **Reason** | **Covered elsewhere?** |
|---|---|---|---|---|
| TC-EMP-054 | Unexpected error inside a menu option | Not implemented | Similar to TC-EMP-055. Both check the same rule (FR-S4): an unexpected exception gives "Unexpected error. See log.", no traceback, a log entry, and the app handles it cleanly. Only the place where the error is raised differs. | TC-EMP-055 |
| TC-EMP-057 | Application log cannot be opened (Python) | Not implemented | Similar to TC-EMP-055. Both are startup failures that must give a friendly message, no traceback and exit code 1 before login, and the Python startup path is the same. | TC-EMP-055 |
| TC-EMP-058 | Database path not set | Not implemented | Similar to TC-EMP-051. A missing EM_DB_PATH and a missing database file are both startup failures with a friendly message and exit code 1. | TC-EMP-051 |
| TC-MGR-045 | Application log cannot be opened | Not implemented | Similar to TC-MGR-043. Both check the FR-S4 rule that an application failure shows a friendly message, no stack trace, and a clean exit with code 1. Only the cause differs: 045 is a log folder that cannot be created, 043 is an unexpected error while running. | TC-MGR-043 |