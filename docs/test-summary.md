# Expense Manager — Test Summary

**Status:** Draft — final review pending  
**Last updated:** 2026-11-12  
**Team:** Shaqayeq Salimy and Vennela Preethi

## Testing completed

Testing covered  SQL validation queries, employee login and logging, expense submission and validation, viewing, editing, deletion and review history. Java tests covered manager authentication, pending expenses, approval and denial rules, reporting and date filters.

Unit tests checked individual rules using controlled dependencies where needed. Integration tests used real temporary SQLite databases. Manual cross-app testing used the shared project database.

## Automated results

| Suite | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Python — PyTest | 280 | 0 | 0 | 0 |
| Java — JUnit | 158 | 0 | 0 | 0 |
| **Total** | **438** | **0** | **0** | **0** |

We designed 127 test cases. Our automated tests ran 438 checks because some cases were tested with several inputs or at different testing levels


The earlier Python run with 279 passes and one failure has been superseded by the confirmed run with 280 passes. The previously failing impossible-date SQL validation check passed in the later run.


## Manual cross-app results

The following Cycle 1 results were recorded by Shaqayeq Salimy:

| Test Case ID | Scenario | Result |
| --- | --- | --- |
| TC-XAPP-001 | Python submission appears in Java | Passed |
| TC-XAPP-002 | Java approval appears in Python | Passed |
| TC-XAPP-003 | Java denial and comment appear in Python | Passed |
| TC-XAPP-007 | Edit refused after manager approval | Passed |
| TC-XAPP-008 | Review refused after employee deletion | Passed |
| TC-MGR-005 | Three consecutive failed logins end the session | Passed |
| TC-MGR-035 | Export report containing records | Passed |
| TC-MGR-036 | Empty report does not offer CSV export | Passed |
| TC-MGR-037 | Verify exported report totals against database | Passed |
| TC-XAPP-001 | Employee submits; manager approves with a comment; employee views history | Passed |
| TC-XAPP-002 | Employee submits; manager denies with a comment | Passed |
| TC-XAPP-003 | Employee opens a pending expense for editing; manager decides it; employee saves the edit | Passed |
| TC-XAPP-004 | Manager lists pending; employee deletes one; manager tries to decide it | Passed |
| TC-XAPP-005 | Employee submits boundary values; Manager App shows the same values | Passed |
| TC-XAPP-006 | Several expenses submitted and decided; manager report totals match employee totals and SQL | Passed |

## Test effectiveness

Shaqayeq temporarily changed the amount validation rule to allow zero. The existing test detected the change: one check failed and five passed. After restoring the original code, all six checks passed again.

The temporary bug was removed and is not an open application defect. Details are recorded in `docs/test-effectiveness.md`.


## Evidence

Evidence is stored under `docs/evidence/`:

- Full Python report: `python-full-cycle2.html`.
- Full Java report: `java-full-cycle2/surefire.html`, with its supporting files.
- Earlier login, logging, submission and assigned Java reports.
- Manual cross-app screenshots.
- Test-effectiveness reports for the temporary bug and restored code.




