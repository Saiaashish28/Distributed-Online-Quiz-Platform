package com.quizsphere.controller;

import com.quizsphere.service.ExportService.ExportFile;
import com.quizsphere.service.QuestionImportService;
import com.quizsphere.service.StudentService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/** Downloadable import templates (.xlsx or .csv). */
@RestController
@RequestMapping("/api/admin/templates")
public class TemplateController {

    private static final List<List<String>> QUESTION_SAMPLE = List.of(
            List.of("What is JVM?", "Java Virtual Machine", "Java Variable Method", "Java Visual Mode",
                    "Java Version Manager", "A", "1", "The JVM executes Java bytecode."),
            List.of("Which protocol is connection-oriented?", "UDP", "TCP", "HTTP", "DNS", "B", "2", ""));

    private static final List<List<String>> ROSTER_SAMPLE = List.of(
            List.of("21CSE001", "Asha Kumar", "asha@example.edu", "III", "CSE", "A", "5", "CS301;CS305", "", ""),
            List.of("22ECE014", "Ravi Shankar", "", "II", "ECE", "A", "3", "", "", ""));

    @GetMapping("/questions")
    public ResponseEntity<byte[]> questions(@RequestParam(defaultValue = "xlsx") String format) throws IOException {
        return build("QuizSphere_Question_Template", QuestionImportService.HEADERS, QUESTION_SAMPLE, format);
    }

    @GetMapping("/students")
    public ResponseEntity<byte[]> students(@RequestParam(defaultValue = "xlsx") String format) throws IOException {
        return build("QuizSphere_Student_Roster_Template", StudentService.ROSTER_HEADERS, ROSTER_SAMPLE, format);
    }

    private ResponseEntity<byte[]> build(String name, List<String> headers, List<List<String>> rows, String format)
            throws IOException {
        if ("csv".equalsIgnoreCase(format)) {
            String csv = "﻿" + csvLine(headers) + "\r\n"
                    + rows.stream().map(TemplateController::csvLine).collect(Collectors.joining("\r\n")) + "\r\n";
            return download(new ExportFile(name + ".csv", csv.getBytes(StandardCharsets.UTF_8)),
                    MediaType.parseMediaType("text/csv"));
        }
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Template");
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(bold);
                sh.setColumnWidth(i, 22 * 256);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sh.createRow(r + 1);
                for (int i = 0; i < rows.get(r).size(); i++) {
                    // Text cells keep values like "5" or register numbers exactly as typed.
                    row.createCell(i, CellType.STRING).setCellValue(rows.get(r).get(i));
                }
            }
            sh.createFreezePane(0, 1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return download(new ExportFile(name + ".xlsx", out.toByteArray()), AdminAssignmentController.XLSX);
        }
    }

    private static String csvLine(List<String> values) {
        return values.stream().map(v -> v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v)
                .collect(Collectors.joining(","));
    }

    private static ResponseEntity<byte[]> download(ExportFile f, MediaType type) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.filename()).build().toString())
                .body(f.content());
    }
}
