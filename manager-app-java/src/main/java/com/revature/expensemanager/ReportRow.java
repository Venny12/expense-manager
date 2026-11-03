package com.revature.expensemanager;

import java.math.BigDecimal;

public record ReportRow (
    String name,
    int count,
    BigDecimal totalAmount,
    BigDecimal approvedAmount
){
}
