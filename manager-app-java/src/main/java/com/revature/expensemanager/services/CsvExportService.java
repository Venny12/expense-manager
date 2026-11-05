package com.revature.expensemanager.services;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.revature.expensemanager.ReportRow;
import com.revature.expensemanager.ReportType;
import com.revature.expensemanager.User;
import com.revature.expensemanager.exceptions.AuthorizationException;

/** Exports the displayed report to CSV (FR-M4). */
public class CsvExportService {

    private final Path reportsFolder;
    private final Clock clock;

    public CsvExportService(Path reportsFolder, Clock clock) {
        this.reportsFolder = reportsFolder;
        this.clock = clock;
    }

    public Path export(
        User user, ReportType type, List<ReportRow> rows
    ) throws IOException {

        if (user == null || !"manager".equals(user.role())) {
            throw new AuthorizationException(
                "Only managers can export reports."
            );
        }

        Files.createDirectories(reportsFolder);

        String timestamp = LocalDateTime.now(clock).format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        );

        String filename = type.name().toLowerCase(Locale.ROOT)
            + "_" + timestamp + ".csv";

        Path output = reportsFolder.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(
                output,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {

            writer.write(
                csv(type.columnTitle())
                + ",Count,Total Amount,Total Approved"
            );
            writer.newLine();

            for (ReportRow row : rows) {
                writer.write(
                    csv(row.name()) + ","
                    + row.count() + ","
                    + row.totalAmount()
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString() + ","
                    + row.approvedAmount()
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString()
                );
                writer.newLine();
            }
        }

        return output;
    }

    private String csv(String value) {
        // Quotes protect names containing commas, quotes, or line breaks.
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}