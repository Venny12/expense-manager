package com.revature.expensemanager;

public enum ReportType {

    EMPLOYEE("Employee"),
    CATEGORY("Category");

    private final String columnTitle;

    ReportType(String columnTitle) {
        this.columnTitle = columnTitle;
    }

    // Heading of the first column
    public String columnTitle() {
        return columnTitle;
    }
    
}
