# Expense Manager — Test Summary

**Team:** Shaqayeq Salimy and Vennela Preethi

## Testing completed

Testing covered SQL validation queries, employee login and logging, expense submission and validation, viewing, editing, deletion and review history. Java tests covered manager authentication, pending expenses, approval and denial rules, reporting and date filters.

Unit tests checked individual rules using controlled dependencies where needed. Integration tests used real temporary SQLite databases. Manual cross-app testing used the shared project database.

### Test cases by area

| Area | Cases | Automated | Manual | Passed | Failed | Not implemented |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Python Employee App (TC-EMP) | 58 | 55 | 0 | 55 | 0 | 3 |
| Java Manager App (TC-MGR) | 45 | 40 | 4 | 43 | 1 | 1 |
| Database (TC-DB) | 27 | 27 | 0 | 27 | 0 | 0 |
| Cross-app (TC-XAPP) | 6 | 0 | 6 | 6 | 0 | 0 |
| **Total** | **136** | **122** | **10** | **131** | **1** | **4** |

Four AI-suggested cases were not implemented because similar cases are already tested: TC-EMP-054, TC-EMP-057, TC-EMP-058 and TC-MGR-045.

## Automated results

| Suite | Passed | Failed | Errors | Skipped | Pass rate |
| --- | ---: | ---: | ---: | ---: | ---: |
| Python — PyTest | 208 | 0 | 0 | 0 | 100% |
| Java — JUnit | 142 | 0 | 0 | 0 | 100% |
| **Total** | **350** | **0** | **0** | **0** | **100%** |

 Our automated tests ran 350 checks because some cases were tested with several inputs or at different testing levels.

## Test case results

| Result | Cycle 1 | Cycle 2 |
| --- | ---: | ---: |
| Passed | 131 (96.3%) | 132 (97.1%) |
| Failed | 1 (0.7%) | 0 (0%) |
| Not implemented (not run) | 4 (2.9%) | 4 (2.9%) |
| **Total** | **136** | **136** |

## Manual results

| Test Case ID | Scenario | Result |
| --- | --- | --- |
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

## Defects

| ID | App | Summary | Severity | Status |
| --- | --- | --- | --- | --- |
| DFT-01 | Java Manager App | Menu required Enter to be pressed twice after login | High | Resolved |
| DFT-02 | Java Manager App | Error creating formatted tables (missing dependencies) | Medium | Resolved |
| DFT-03 | Java Manager App | Approve/deny prompt appeared before the "no longer pending" check | Medium | Resolved |
| DFT-04 | Java Manager App | Ctrl+C displayed "Unexpected error. See log." | Low | Resolved |

No defects are open. Details are in `docs/defect_log.md`.

## Test effectiveness

Python: Shaqayeq temporarily changed the amount validation rule to allow zero. The existing test detected the change: one check failed and five passed. After restoring the original code, all six checks passed again.

Java: Vennela temporarily moved the approval comment limit from 200 to 201 characters. The existing tests detected it: three checks failed and 19 passed. After restoring the original code, all 22 checks passed again.

The temporary bugs were removed and are not open application defects. Details are recorded in `docs/test-effectiveness.md`.

## Evidence

Evidence is stored under `docs/evidence/`:

- Full Python report: `pytest-final-report.html`.
- Full Java report: `surefire-final/surefire.html`, with its supporting files.
- Earlier login, logging, submission and Java Cycle 1 reports: `Additional/`.
- Manual cross-app screenshots: `XAPP-manual-tests/`. Manual report and login screenshots: `docs/manual-tests/`.
- Test-effectiveness reports for the temporary bugs and restored code.
