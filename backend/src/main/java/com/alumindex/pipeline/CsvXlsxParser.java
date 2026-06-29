package com.alumindex.pipeline;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streams CSV / XLSX / XLS uploads row-by-row with BOUNDED memory.
 *
 * The whole file is never held in heap: CSV is consumed lazily through Commons CSV,
 * and XLSX is read with POI's SAX/event model ({@link XSSFReader} +
 * {@link XSSFSheetXMLHandler}) rather than the {@code XSSFWorkbook} full DOM that
 * OOM-crashes on large files. Callers receive each row via a {@link RowHandler}
 * push callback and decide how to batch it. Legacy {@code .xls} (HSSF) has no
 * streaming reader and is read in full — acceptable because the .xls format caps
 * at 65,536 rows, so a 100K-row .xls is impossible.
 *
 * Header recognition and the name-column requirement live in {@link HeaderMapper}.
 */
@Component
public class CsvXlsxParser {

    /** Generous cap — multipart is disk-backed by the servlet container, not heap. */
    private static final long MAX_BYTES = 200L * 1024 * 1024;   // 200 MB

    /** Push callback invoked once per data row (the header row is excluded). */
    public interface RowHandler { void row(Map<String, String> row); }

    /** Header list plus a few sample data rows — enough for the validation gate. */
    public record Peek(List<String> headers, List<Map<String, String>> sampleRows) {}

    /** Internal early-exit signal used by {@link #peek} to stop after N rows. */
    private static final class StopSignal extends RuntimeException {
        StopSignal() { super(null, null, false, false); }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Reads only the header + up to {@code sampleSize} data rows (cheap; validation gate). */
    public Peek peek(Path file, String filename, int sampleSize) throws IOException {
        var headers = new ArrayList<String>();
        var samples = new ArrayList<Map<String, String>>();
        try {
            stream(file, filename, headers, row -> {
                samples.add(row);
                if (samples.size() >= sampleSize) throw new StopSignal();
            });
        } catch (StopSignal ignored) {
            // reached the sample limit — expected
        }
        return new Peek(headers, samples);
    }

    /** Counts data rows without retaining them — for progress reporting. */
    public int countRows(Path file, String filename) throws IOException {
        int[] n = {0};
        stream(file, filename, new ArrayList<>(), row -> n[0]++);
        return n[0];
    }

    /** Streams every data row to {@code handler}; {@code headersOut} is populated with the header names. */
    public void stream(Path file, String filename, List<String> headersOut, RowHandler handler) throws IOException {
        String f = filename == null ? "" : filename.toLowerCase();
        checkType(f);
        checkSize(file);
        if (f.endsWith(".csv"))        streamCsv(file, headersOut, handler);
        else if (f.endsWith(".xlsx"))  streamXlsx(file, headersOut, handler);
        else                           streamXls(file, headersOut, handler);
    }

    // ── Validation helpers ─────────────────────────────────────────────────────

    private void checkType(String f) {
        if (!(f.endsWith(".csv") || f.endsWith(".xlsx") || f.endsWith(".xls"))) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Only CSV, XLSX, and XLS are accepted");
        }
    }

    private void checkSize(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "File size " + size + " bytes exceeds the 200 MB limit");
        }
    }

    // ── CSV (lazy) ─────────────────────────────────────────────────────────────

    private void streamCsv(Path file, List<String> headersOut, RowHandler handler) throws IOException {
        var format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true).setTrim(true).build();
        try (var reader = new InputStreamReader(
                        new BufferedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8);
             var parser = CSVParser.parse(reader, format)) {

            List<String> names = parser.getHeaderNames();   // file order (getHeaderMap is unordered)
            headersOut.addAll(names);
            for (var record : parser) {
                var row = new LinkedHashMap<String, String>();
                for (int i = 0; i < names.size(); i++) {
                    row.put(names.get(i), i < record.size() ? record.get(i) : "");
                }
                handler.row(row);
            }
        }
    }

    // ── XLSX (streaming SAX) ────────────────────────────────────────────────────

    private void streamXlsx(Path file, List<String> headersOut, RowHandler handler) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            var sst = new ReadOnlySharedStringsTable(pkg);
            var reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();

            var sheets = reader.getSheetsData();
            if (!sheets.hasNext()) throw new IllegalArgumentException("Spreadsheet has no sheets");

            XMLReader xml = XMLHelper.newXMLReader();
            xml.setContentHandler(new XSSFSheetXMLHandler(
                    styles, sst, new SaxRowHandler(headersOut, handler), false));
            try (InputStream sheet = sheets.next()) {
                xml.parse(new InputSource(sheet));
            }
        } catch (StopSignal s) {
            throw s;   // early-exit from peek — let it propagate
        } catch (Exception e) {
            if (containsStop(e)) throw new StopSignal();
            throw new IOException("Failed to read XLSX: " + e.getMessage(), e);
        }
    }

    private static boolean containsStop(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof StopSignal) return true;
        }
        return false;
    }

    /** SAX content handler: row 0 → headers (by column index), later rows → maps pushed to the handler. */
    private static final class SaxRowHandler implements SheetContentsHandler {
        private final List<String> headersOut;
        private final RowHandler handler;
        private final Map<Integer, String> headerByCol = new HashMap<>();
        private Map<String, String> current;
        private boolean inHeader;

        SaxRowHandler(List<String> headersOut, RowHandler handler) {
            this.headersOut = headersOut;
            this.handler = handler;
        }

        @Override public void startRow(int rowNum) {
            inHeader = rowNum == 0;
            current = inHeader ? null : new LinkedHashMap<>();
        }

        @Override public void cell(String cellRef, String value, XSSFComment comment) {
            int col = cellRef == null
                    ? (inHeader ? headerByCol.size() : current.size())
                    : new CellReference(cellRef).getCol();
            String v = value == null ? "" : value.trim();
            if (inHeader) {
                String name = v.toLowerCase().replace(' ', '_');
                headerByCol.put(col, name.isEmpty() ? "column_" + (col + 1) : name);
            } else {
                current.put(headerByCol.getOrDefault(col, "column_" + (col + 1)), v);
            }
        }

        @Override public void endRow(int rowNum) {
            if (rowNum == 0) {
                int max = headerByCol.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                for (int c = 0; c <= max; c++) {
                    headersOut.add(headerByCol.getOrDefault(c, "column_" + (c + 1)));
                }
            } else if (current != null) {
                handler.row(current);
            }
        }

        @Override public void headerFooter(String text, boolean isHeader, String tagName) { }
    }

    // ── XLS (legacy HSSF, full DOM — small files only) ──────────────────────────

    private void streamXls(Path file, List<String> headersOut, RowHandler handler) throws IOException {
        try (var in = Files.newInputStream(file); var wb = new HSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalArgumentException("Spreadsheet has no header row");

            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String name = cellToString(cell).trim().toLowerCase().replace(' ', '_');
                headersOut.add(name.isEmpty() ? "column_" + (c + 1) : name);
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                var map = new LinkedHashMap<String, String>();
                for (int c = 0; c < headersOut.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    map.put(headersOut.get(c), cellToString(cell));
                }
                handler.row(map);
            }
        }
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                    ? cell.getStringCellValue()
                    : String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }
}
