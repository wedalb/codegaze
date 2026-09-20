package io.codegaze.core;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class Csv {
    private Csv() {}
    public static String row(Object... cells) {
        return Arrays.stream(cells).map(Csv::cell).collect(Collectors.joining(",")) + "\n";
    }
    public static String cell(Object value) {
        if (value == null) return "\"\"";
        String s = value.toString();
        // Keep source text from becoming spreadsheet formulas when CSV is opened.
        if (!s.isEmpty() && "=+-@\t\r\n".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
