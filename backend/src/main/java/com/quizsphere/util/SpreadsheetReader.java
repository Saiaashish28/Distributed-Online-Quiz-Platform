package com.quizsphere.util;

import com.quizsphere.exception.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads the first sheet of an uploaded .xlsx or .csv file into rows keyed by normalized
 * header (lowercase alphanumerics, e.g. "Option A" -> "optiona"). Formulas are never
 * evaluated; their cached values are used.
 */
public final class SpreadsheetReader {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ROWS = 5000;

    private SpreadsheetReader() {
    }

    public record Row(int rowNumber, Map<String, String> values) {
        public String get(String normalizedHeader) {
            String v = values.get(normalizedHeader);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    public record Sheet(String fileName, List<String> rawHeaders, Set<String> headers, List<Row> rows) {
    }

    public static String normalizeHeader(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Sheet read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a non-empty .xlsx or .csv file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("File is too large (max 5 MB)");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("upload").trim();
        String lower = name.toLowerCase(Locale.ROOT);
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            if (lower.endsWith(".xlsx")) {
                in.mark(4);
                byte[] magic = in.readNBytes(2);
                in.reset();
                if (magic.length < 2 || magic[0] != 'P' || magic[1] != 'K') {
                    throw ApiException.badRequest("The file is not a valid .xlsx workbook");
                }
                return readXlsx(name, in);
            }
            if (lower.endsWith(".csv")) {
                return readCsv(name, in);
            }
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read");
        }
        throw ApiException.badRequest("Unsupported file type. Upload a .xlsx or .csv file");
    }

    private static Sheet readXlsx(String name, InputStream in) throws IOException {
        Workbook wb;
        try {
            wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            throw ApiException.badRequest("The file is not a valid .xlsx workbook");
        }
        try (wb) {
            if (wb.getNumberOfSheets() == 0) {
                throw ApiException.badRequest("The workbook has no sheets");
            }
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            fmt.setUseCachedValuesForFormulaCells(true);

            org.apache.poi.ss.usermodel.Row headerRow = null;
            int headerIndex = -1;
            for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum() && i >= 0; i++) {
                org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
                if (r != null && !isBlankRow(r, fmt)) {
                    headerRow = r;
                    headerIndex = i;
                    break;
                }
            }
            if (headerRow == null) {
                throw ApiException.badRequest("The file is empty");
            }
            List<String> raw = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                raw.add(fmt.formatCellValue(headerRow.getCell(c)).trim());
            }
            List<String> keys = raw.stream().map(SpreadsheetReader::normalizeHeader).toList();

            List<Row> rows = new ArrayList<>();
            for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
                if (r == null || isBlankRow(r, fmt)) continue;
                if (rows.size() >= MAX_ROWS) {
                    throw ApiException.badRequest("Too many rows (max " + MAX_ROWS + ")");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < keys.size(); c++) {
                    if (keys.get(c).isEmpty()) continue;
                    values.put(keys.get(c), fmt.formatCellValue(r.getCell(c)));
                }
                rows.add(new Row(i + 1, values));
            }
            return new Sheet(name, raw, new LinkedHashSet<>(keys), rows);
        }
    }

    private static boolean isBlankRow(org.apache.poi.ss.usermodel.Row r, DataFormatter fmt) {
        for (Cell cell : r) {
            if (!fmt.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    private static Sheet readCsv(String name, InputStream in) throws IOException {
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true)
                .setTrim(true).setAllowMissingColumnNames(true).setIgnoreSurroundingSpaces(true)
                .setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.ALLOW_ALL)
                .get();
        try (CSVParser parser = CSVParser.parse(reader, format)) {
            List<String> raw = parser.getHeaderNames().stream()
                    .map(h -> h.replace("﻿", "").trim()).toList();
            if (raw.isEmpty()) {
                throw ApiException.badRequest("The file is empty");
            }
            List<String> keys = raw.stream().map(SpreadsheetReader::normalizeHeader).toList();
            List<Row> rows = new ArrayList<>();
            for (CSVRecord rec : parser) {
                boolean blank = true;
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < keys.size() && c < rec.size(); c++) {
                    String v = rec.get(c);
                    if (v != null && !v.isBlank()) blank = false;
                    if (!keys.get(c).isEmpty()) values.put(keys.get(c), v);
                }
                if (blank) continue;
                if (rows.size() >= MAX_ROWS) {
                    throw ApiException.badRequest("Too many rows (max " + MAX_ROWS + ")");
                }
                rows.add(new Row((int) rec.getRecordNumber() + 1, values));
            }
            return new Sheet(name, raw, new LinkedHashSet<>(keys), rows);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw ApiException.badRequest("The CSV file could not be parsed: check the header row and quoting");
        }
    }
}
