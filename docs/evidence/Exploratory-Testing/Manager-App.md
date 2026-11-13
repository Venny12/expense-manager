# Exploratory Testing - Manager App (Java)

## 1. Session Information

| Field | Details |
|---|---|
| Tester | Vennela Preethi |
| Application | Java Manager App |
| Session length | About 30 minutes |
| Date | 2026-11-12 |

## 2. Objective

The goal of this session was to explore the Java Manager App and check for any unexpected behaviour that might not be covered by the 45 designed test cases (TC-MGR-001 to TC-MGR-045).

I focused on trying unusual inputs, invalid values, different sequences of actions and interruptions to see how the application handled them.

## 3. Session Notes

| Charter | Inputs to Try | Technique | What I Gave as Inputs | What I Saw | Bug / Question? |
|---|---|---|---|---|---|
| **C1 - Login Oddities** | Leading/trailing spaces, uppercase username, long username (50 characters), passwords with spaces, tab input and incorrect passwords. | Error guessing | Tried different username and password combinations, including spaces, long inputs and invalid credentials. I also entered the wrong password multiple times and tried logging in with the correct password after two failed attempts. | The application rejected invalid credentials and modified usernames. After three consecutive failed login attempts, it displayed a friendly message indicating too many failed attempts. No stack trace or technical error was observed. | No defects found. Login validation and failed-attempt handling worked as expected. |
| **C2 - Main Menu Input** | Blank, space, 00, +1, 1 with trailing space, full-width １, very long text, negative numbers and numbers above the menu limit. | Equivalence partitioning, boundary value analysis | Tried numbers, alphabets, long strings, negative numbers, combinations of numbers and letters, 0000 and 00000000. | All invalid inputs displayed "Please enter a number from the menu." Entering leading zeros is exit as this is consistent with the shared validation data, which allows leading zeros.| No defects found. Leading zeros are valid, so exiting on 00 is expected.”
| **C3 - Review Expense** | Expense IDs with spaces or leading zeros, -1, 0, 2147483648, 1.0; invalid actions; blank comments and special characters. | Boundary value analysis, error guessing | Tried negative numbers, zero, long strings and combinations of letters, text with special characters and numbers when entering expense IDs. | The application handled the invalid inputs and boundary values as expected. No unexpected behaviour was noticed during the scenarios I tried. | No defects found. |
| **C4 - State Changes** | Approve an expense and try the same ID again; check pending expenses after decisions; check expenses changed through the Employee App. | State transition testing | Approved an expense in the Manager App, then switched to the Employee App to list and delete expenses. I returned to the Manager App to check whether the changes were reflected. | The changes appeared promptly. Expense statuses were updated correctly, and both applications showed the latest information. | No defects found. State changes and updates between the two applications worked as expected. |
| **C5 - Reports and Dates** | Both report types without filters; start and end dates; future dates; invalid dates and formats; start date after end date. | Boundary value analysis, decision table testing | Tried different start and end dates, including future dates, invalid dates and incorrect date formats. | The application displayed appropriate error messages: "Dates must be in YYYY-MM-DD format." and "The start date must be on or before the end date." I also noticed that when the end date was in the future, the reports showed data only up to the current date. | No defects found in the scenarios tested. Date validation and future-date handling worked as expected. |
| **C6 - Interruptions** | Ctrl+C at login, menu, expense review, report and export prompts. | Error guessing | Pressed Ctrl+C at different stages, including login, menu navigation, reports and CSV export. | The application closed gracefully and displayed "Application closed." instead of showing an unexpected error. This also confirms that the earlier DFT-04 issue, where Ctrl+C displayed "Unexpected error. See log.", has been fixed. | No defects found.  |
| **C7 - CSV Export** | Export multiple CSV files; check the export location and behaviour when reports are empty. | Error guessing | Exported CSV files and checked whether they were saved in the correct folder. I also checked what happened when there was no data in the reports. | The CSV files were saved in the expected folder. When the reports were empty, the application did not unnecessarily ask me to export them. | No defects found. |

## 4. Overall Observations

- I explored all seven charters, covering login, menu input, expense review, state changes, reports, interruptions and CSV export.
- Login validation worked as expected. Invalid credentials were rejected, and the application displayed a friendly message after three consecutive failed attempts.
- All invalid menu inputs were handled correctly.
- Expense review and state changes worked as expected. Updates made through the Employee App were also reflected in the Manager App.
- Report date validation displayed the correct error messages for invalid formats and date ranges. Future end dates were also handled correctly.
- Ctrl+C worked as expected at the different prompts I tested. The application displayed "Application closed." without showing an unexpected error.
- CSV files were saved in the correct folder, and the application handled empty reports appropriately.

## 5. Conclusion

I completed all seven exploratory testing charters for the Java Manager App within approximately 30 minutes.

No new defects were found during this session. 
I also verified that the earlier DFT-04 issue related to Ctrl+C has been fixed, as the application now closes gracefully with the message "Application closed."