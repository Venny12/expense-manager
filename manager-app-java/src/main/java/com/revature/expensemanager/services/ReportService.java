package com.revature.expensemanager.services;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.revature.expensemanager.ReportRow;
import com.revature.expensemanager.ReportType;
import com.revature.expensemanager.User;
import com.revature.expensemanager.dao.ReportDao;
import com.revature.expensemanager.exceptions.AuthorizationException;
import com.revature.expensemanager.exceptions.DatabaseException;
import com.revature.expensemanager.validation.InputValidator.DateRange;

/** Builds the manager spending reports (FR-M3). */
public class ReportService {

    private static final Logger LOGGER =
        Logger.getLogger(ReportService.class.getName());

    private final ReportDao reportDao;

    public ReportService(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    /** Totals per employee or category. A null range means all dates. */
    public List<ReportRow> report(User user, ReportType type,
                                  DateRange range) {
        requireManager(user);

        try {
            return reportDao.findTotals(type, range);
        } catch (SQLException error) {
            LOGGER.log(Level.SEVERE, "Could not load the report.", error);
            throw new DatabaseException("Could not load the report.", error);
        }
    }

    // FR-A4: the service checks who is asking, not only the menu.
    private void requireManager(User user) {
        if (user == null || !"manager".equals(user.role())) {
            throw new AuthorizationException(
                "Only managers can view reports."
            );
        }
    }
}