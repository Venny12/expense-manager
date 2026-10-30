package com.revature.expensemanager;

/** Account information returned after a successful login. */
public record User(
    int id,
    String username,
    String role,
    String fullName
) {
}

