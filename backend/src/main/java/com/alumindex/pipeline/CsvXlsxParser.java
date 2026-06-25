package com.alumindex.pipeline;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses CSV / XLSX / XLS uploads into a flat list of string maps.
 * Enforces the file-type and 50 MB size limits; header recognition and
 * the name-column requirement live in {@link HeaderMapper}.
 */
@Component
public class CsvXlsxParser {

    private static final long MAX_BYTES = 50L * 1024 * 1024;   // exactly 50.00 MB

    public record ParseResult(List<Map<String, String>> rows, List<String> errors) {}

    public ParseResult parse(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "File size " + file.getSize() + " bytes exceeds the 50 MB limit");
        }

        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();

        if (filename.endsWith(".csv")) {
            return parseCsv(file);
        } else if (filename.endsWith(".xlsx")) {
            return parseXlsx(file, false);
        } else if (filename.endsWith(".xls")) {
            return parseXlsx(file, true);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type. Only CSV, XLSX, and XLS are accepted");
        }
    }

    private ParseResult parseCsv(MultipartFile file) throws IOException {
        var format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true).setTrim(true).build();
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVParser.parse(reader, format)) {

            var rows = new ArrayList<Map<String, String>>();
            var errors = new ArrayList<String>();
            int rowNum = 2;

            for (var record : parser) {
                try {
                    var row = new LinkedHashMap<String, String>();
                    parser.getHeaderMap().forEach((col, idx) -> row.put(col, record.get(idx)));
                    rows.add(row);
                } catch (Exception e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                }
                rowNum++;
            }
            return new ParseResult(rows, errors);
        }
    }

    private ParseResult parseXlsx(MultipartFile file, boolean isLegacy) throws IOException {
        try (var wb = isLegacy
                ? new HSSFWorkbook(file.getInputStream())
                : new XSSFWorkbook(file.getInputStream())) {

            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalArgumentException("Spreadsheet has no header row");

            var headers = new ArrayList<String>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String name = cellToString(cell).trim().toLowerCase().replace(' ', '_');
                headers.add(name.isEmpty() ? "column_" + (c + 1) : name);
            }
            var rows = new ArrayList<Map<String, String>>();
            var errors = new ArrayList<String>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                try {
                    var map = new LinkedHashMap<String, String>();
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        map.put(headers.get(c), cellToString(cell));
                    }
                    rows.add(map);
                } catch (Exception e) {
                    errors.add("Row " + (r + 1) + ": " + e.getMessage());
                }
            }
            return new ParseResult(rows, errors);
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
