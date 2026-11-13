# Test Effectiveness Report - Python and Java

## Python check

**Tester:** Shaqayeq Salimy  
**Date:** 2026-11-10  
**Requirement:** VR-01 — expense amount must be greater than zero  
**Related test case:** TC-EMP-017

### Purpose

Check whether our existing test catches a bug that wrongly allows zero
as an expense amount.

### Test used

File: `employee-app-python/tests/unit/test_submission_validation.py`

Test: `test_invalid_amount_is_rejected[0]`

The test function checks six invalid inputs. We ran all six during
the experiment.

### Temporary bug

In `employee-app-python/employee_app/validators.py`, we changed:

Original:
```python
if amount <= 0 or amount > Decimal("10000.00"):
```

Temporary bug:
```python
if amount < 0 or amount > Decimal("10000.00"):
```

Removing the equals sign allowed zero to pass validation.

### Results

| Stage | Result |
|---|---|
| Before introducing the bug | 6 passed |
| With the temporary bug | 1 failed, 5 passed |
| After restoring the original code | Pending verification |

The zero-input test failed with:

`Failed: DID NOT RAISE ValidationError`

It expected a validation error for zero, but the changed code accepted
zero. This shows that the test caught the deliberate bug.

### Evidence

- Bug detected: `docs/evidence/python-effectiveness-bug.html`
- Code restored: `docs/evidence/python-effectiveness-restored.html`

### Conclusion

The existing test caught the deliberate zero-amount bug.
After restoring the original code, all six checks passed again.
The temporary bug was removed.


=================================================================================================

## Java check

**Tester:** Vennela Preethi  
**Date:** 2026-11-11  
**Requirement:** VR-06 — Approval comment is optional, 0–200 characters 
(empty is OK, 201 characters is rejected)  
**Related test case:** TC-MGR-016 — Approve Expense, approval comment of 200 and 201 characters

### Purpose

Check whether our existing tests catch a bug that wrongly allows an approval comment of 201 characters, one character over the VR-06 limit.

### Test used

**File:** `manager-app-java/src/test/java/com/revature/expensemanager/ApproveExpenseTest.java`

**Tests:**

- `ApprovalRules.approvalCommentLengthLimit` (unit, 4 cases: 200 accepted, 201 rejected, and both again with spaces around the comment).
- `ApprovalWithRealDatabase.tooLongCommentAsksAgainAndSavesNothing` (integration, a 201-character comment through the real menu).

All 22 tests in the file were run during the experiment.

### Temporary bug

In `manager-app-java/src/main/java/com/revature/expensemanager/validation/CommentValidator.java`, method `isValidApproval`, we changed:

**Original:**

```java
return comment == null || comment.trim().length() <= MAX_LENGTH;
```

**Temporary bug:**

```java
return comment == null || comment.trim().length() <= MAX_LENGTH + 1;
```

The limit moved from 200 to 201, so a 201-character comment passed validation.

### Results

| Stage | Result |
|---|---|
| Before introducing the bug | 22 passed |
| With the temporary bug | 3 failed, 19 passed |
| After restoring the original code | 22 passed |

**The three failing tests:**

1. `approvalCommentLengthLimit[TC-MGR-016-201-characters]` — expected a `ValidationException`, but the comment was accepted and the code went on to the database lookup (`NotFoundException`).
2. `approvalCommentLengthLimit[TC-MGR-016-201-plus-outer-spaces]` — the same failure with spaces around the comment.
3. `tooLongCommentAsksAgainAndSavesNothing` — the 201-character comment was accepted instead of the menu asking again.

The 200-character cases still passed, as they should: the bug only moved the upper limit. This shows that the tests catch the deliberate bug.


### Evidence

- Bug detected: `docs/evidence/java-effectiveness-bug.html`
- Code restored: `docs/evidence/java-effectiveness-restore.html`

These two files are console output from the run described above. 
For the official report, repeat the run with Maven on the team machine (steps below).

1. Make the temporary change, then run:

   ```powershell
   mvn test -Dtest=ApproveExpenseTest surefire-report:report-only
   ```

   Keep the report as `java-effectiveness-bug.html`.

2. Restore the original line, run the same command, and keep the report as `java-effectiveness-restore.html`.

### Conclusion

The existing tests caught the deliberate 201-character bug. After restoring the original code, all 22 tests passed again. The temporary bug was removed.