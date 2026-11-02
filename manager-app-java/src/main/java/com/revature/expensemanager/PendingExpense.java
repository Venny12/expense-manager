package com.revature.expensemanager;

import java.math.BigDecimal;

/** Details shown when a manager lists pending expenses. */
public record PendingExpense(
    int id,
    String employeeName,
    String expenseDate,
    String category,
    BigDecimal amount,
    String description
) {
}