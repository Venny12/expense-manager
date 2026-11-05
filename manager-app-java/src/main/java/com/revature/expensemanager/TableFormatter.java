package com.revature.expensemanager;

import java.util.ArrayList;
import java.util.List;

import de.vandermeer.asciitable.AT_Row;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;

public final class TableFormatter {

    private TableFormatter() { // helper class
    }

    /**
     * Returns a bordered table as text.
     *
     * @param headers     column titles (centered)
     * @param rows        one String[] per row, same length as headers
     * @param rightAlign  true for columns that hold numbers
     * @param maxWidths   longest line in a column; longer text wraps.
     *                    Use 0 for a column that should never wrap.
     */
    public static String format(String[] headers, List<String[]> rows, boolean[] rightAlign, int[] maxWidths) {
        AsciiTable table = new AsciiTable();
        table.getRenderer().setCWC(new CWC_LongestLine());
        table.setPaddingLeftRight(1);

        table.addRule();
        AT_Row headerRow = table.addRow((Object[]) headers);
        for (int column = 0; column < headers.length; column++) {
            headerRow.getCells().get(column).getContext().setTextAlignment(TextAlignment.CENTER);
        }
        table.addRule();

        for (String[] row : rows) {
            Object[] cells = new Object[row.length];
            for (int column = 0; column < row.length; column++) {
                cells[column] = wrap(row[column], maxWidths[column]);
            }

            AT_Row dataRow = table.addRow(cells);
            for (int column = 0; column < row.length; column++) {
                dataRow.getCells().get(column).getContext().setTextAlignment(rightAlign[column] ? TextAlignment.RIGHT : TextAlignment.LEFT);
            }
        }

        table.addRule();
        return table.render();
    }

    // Splits long text into lines of at most `width` characters, at spaces where possible
    // An empty value becomes "" because the library treats null as "merge with the cell on the left".
    static String wrap(String text, int width) {
        if (text == null) {
            return "";
        }
        if (width <= 0 || text.length() <= width) {
            return text;
        }

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.trim().split("\\s+")) {
            while (word.length() > width) {            // one very long word
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }

            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }

            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return String.join("\n", lines);
    }
}