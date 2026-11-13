# Defect Log

This log records defects identified during application development, before the formal testing cycles began. Defects discovered during formal testing are documented separately in the test reports.

## 1. Defect Summary

| ID | Date Found | App | Summary | Severity / Priority | Found By | Reverified By | Status | Fix Reference |
|---|---|---|---|---|---|---|---|---|
| DFT-01 | 2026-11-01 | Java Manager App | Menu required Enter to be pressed twice after login due to a Scanner input issue. | High / Medium Priority | Vennela | Vennela | Resolved | Commit `e0332cd` |
| DFT-02 | 2026-11-04 | Java Manager App | Error creating formatted tables due to missing dependencies. | Medium / Low Priority | Vennela | Vennela/Shaqayeq | Resolved | Commit `2217588` (PR #22) |
| DFT-03 | 2026-11-03 | Java Manager App | Approve/deny prompt appeared before checking whether an expense was still pending. | Medium / High Priority | Vennela | Vennela | Resolved | Commit `f537845` (PR #20) |
| DFT-04 | 2026-11-11 | Java Manager App | Pressing Ctrl+C displayed "Unexpected error. See log." and recorded a SEVERE error. | Low / Medium Priority | Vennela | Vennela | Resolved | Commit `2826d91` (PR #46) |

The defect IDs 1, 2, 3 are found during the development phase and defect 4 found during the testing.
---

## 2. Defect Details

### DFT-01: Manager Menu Required Enter to Be Pressed Twice (Scanner Issue)

- **Found in:** Java Manager App, after login, in the main menu.
- **Symptom:** The first Enter press in the menu was ignored. The manager had to press Enter twice before the menu choice was accepted.
- **Root cause:** Two different readers were reading the same keyboard input. The login prompts used `System.console()`, but after login, `Main` started the menu using `new Scanner(System.in)`. Since the two readers do not share their input buffers, the first line was consumed by the wrong reader.
- **Fix:** The menu now uses the same `Console` instance as the login prompts through `new Menu(user, connection, console)`. A Scanner-based constructor is retained only for tests that provide scripted input. The unused Scanner and duplicate imports were removed from `Main`.
- **Files changed:** `Main.java`, `Menu.java`.
- **Verification:** Log in and select menu options using a single Enter press each. The Java menu-flow test (`TC-MGR-039`) covers the login, menu, and exit session.
- **Lesson learned:** Use one input source throughout the application session.

### DFT-02: Error Creating Formatted Tables (Dependency Error)

- **Found in:** Java Manager App, while adding the formatted table display for pending expenses.
- **Symptom:** The build or application failed when the table code was added because the required table library could not be resolved.
- **Root cause:** The table library was missing from `pom.xml`. The `TableFormatter` class uses `AsciiTable`, and its logging required an SLF4J binding.
- **Fix:** Added `de.vandermeer:asciitable:0.3.2` and `org.slf4j:slf4j-simple:1.7.12` to `manager-app-java/pom.xml`, then reviewed and rebuilt the application.
- **Files changed:** `pom.xml`, `TableFormatter.java`, `Menu.java`.
- **Verification:** Confirmed that pending expenses display correctly in a formatted table.
- **Lesson learned:** Add required libraries to `pom.xml` before writing code that imports them, and run `mvn compile` immediately to catch dependency issues early.

### DFT-03: Approve/Deny Prompt Appeared Before the "No Longer Pending" Check

- **Found in:** Java Manager App, Review Expense functionality.
- **Symptom:** The application asked the manager to approve or deny an expense and enter a comment before checking whether the expense had already been decided or existed. Only afterwards did it display "Expense is no longer pending."
- **Impact:** No data was changed because the update only applies to pending expenses. However, the manager had to go through unnecessary prompts, and the message appeared too late.
- **Severity / Priority:** Medium / High.
- **Requirement:** FR-M2, FR-S2.
- **Test cases:** `TC-MGR-015`, `TC-MGR-024`.
- **Fix:** The pending-status check (`ExpenseService.requirePending`) now runs inside `Menu.reviewExpense` before the approve/deny prompt is displayed.
- **Files changed:** `Menu.java`, `ExpenseService.java`.
- **Verification:** Enter an expense ID that has already been decided and another ID that does not exist. The application should display the appropriate message immediately, without asking the manager to approve or deny the expense.

### DFT-04: Ctrl+C Displayed "Unexpected Error. See Log."

- **Found in:** Java Manager App, at the Username and Password login prompts and inside the menu.

**Steps to reproduce:**

1. Start the Manager App.
2. At the `Username:` prompt, enter `manager_asha` and press Enter.
3. At the `Password:` prompt, press Ctrl+C.

**Expected result:**

The application should close cleanly, display `Application closed.`, and exit with code 0. No error should be written to the log.

The Python Employee App already behaves this way (`TC-EMP-056`).

**Actual result:**

The application displays:

```text
Username: manager_asha
Password:
Unexpected error. See log.
```

It exits with code 1 and writes `Unexpected application error.` as a SEVERE log entry.

- **Impact:** No data is changed, but a normal user action looks like an application crash and creates a false error in the log.
- **Severity / Priority:** Low / Medium.
- **Requirement:** FR-S4 (friendly errors).
- **Root cause:** `Main` only treated a `null` value returned by the console as an indication that input had ended. When Ctrl+C interrupted the console, it threw an exception instead. Since the exception was not an `ExpenseManagerException`, it reached the final `catch (Exception)` block in `Main.run`, which displayed "Unexpected error. See log."
- **Fix:** Added `Main.isConsoleInterrupt`, which recognises a console interrupt (`IOError`, `UncheckedIOException`, or the console's user-interrupt exception), including when it appears as the cause of another exception.
  - In `Main.run`, an interrupt now displays `Application closed.` and returns exit code 0.
  - In `Menu`, the catch block around menu options rethrows an interrupt. This ensures that pressing Ctrl+C inside Review or Reports also closes the application instead of returning to the menu.
- **Files changed:** `Main.java`, `Menu.java`.
- **Test cases:** `TC-MGR-044` (automated, `ErrorHandlingIntegrationTest`). The test simulates Ctrl+C at the Username, Password, and menu prompts, as well as inside Review. It failed before the fix and passes after the fix.
- **Verification:** Press Ctrl+C manually at the Username prompt, Password prompt, menu prompt, and inside Review Expense. Each time, the application should display `Application closed.` and exit with code 0.
- **Lesson learned:** Treat a console interrupt as a normal way to exit the application, not as an unexpected error.
