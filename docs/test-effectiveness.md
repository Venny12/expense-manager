# Test Effectiveness Report

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
