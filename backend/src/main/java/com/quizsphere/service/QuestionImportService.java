package com.quizsphere.service;

import com.quizsphere.dto.QuizDtos.QuestionImportRow;
import com.quizsphere.entity.Question;
import com.quizsphere.util.SpreadsheetReader;
import com.quizsphere.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/** Parses and validates the question import template (PRD section 5). */
@Service
public class QuestionImportService {

    public static final List<String> HEADERS = List.of("Question", "Option A", "Option B", "Option C", "Option D",
            "Correct Answer", "Marks", "Explanation");
    private static final List<String> REQUIRED = List.of("question", "optiona", "optionb", "correctanswer", "marks");
    private static final List<String> OPTION_KEYS = List.of("optiona", "optionb", "optionc", "optiond");
    private static final String LETTERS = "ABCD";

    public record Parsed(String fileName, List<String> headerErrors, List<QuestionImportRow> rows) {
    }

    public Parsed parse(MultipartFile file) {
        SpreadsheetReader.Sheet sheet = SpreadsheetReader.read(file);
        List<String> headerErrors = new ArrayList<>();
        for (String h : REQUIRED) {
            if (!sheet.headers().contains(h)) {
                headerErrors.add("Missing required column: " + HEADERS.stream()
                        .filter(x -> SpreadsheetReader.normalizeHeader(x).equals(h)).findFirst().orElse(h));
            }
        }
        Set<String> known = new HashSet<>(HEADERS.stream().map(SpreadsheetReader::normalizeHeader).toList());
        List<String> unknown = sheet.rawHeaders().stream()
                .filter(h -> !h.isBlank() && !known.contains(SpreadsheetReader.normalizeHeader(h))).toList();
        if (!headerErrors.isEmpty()) {
            return new Parsed(sheet.fileName(), headerErrors, List.of());
        }
        List<QuestionImportRow> rows = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        for (SpreadsheetReader.Row r : sheet.rows()) {
            QuestionImportRow row = parseRow(r, seenQuestions);
            if (!unknown.isEmpty() && rows.isEmpty()) {
                row.warnings().add("Ignored unknown column(s): " + String.join(", ", unknown));
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            headerErrors.add("The file has a header row but no questions");
        }
        return new Parsed(sheet.fileName(), headerErrors, rows);
    }

    private QuestionImportRow parseRow(SpreadsheetReader.Row r, Set<String> seenQuestions) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String text = r.get("question");
        if (text == null) errors.add("Question text is required");
        else if (text.length() > 4000) errors.add("Question text is too long (max 4000)");
        else if (!seenQuestions.add(text.toLowerCase(Locale.ROOT))) warnings.add("Same question text appears earlier in the file");

        List<String> options = new ArrayList<>();
        boolean gap = false;
        for (int i = 0; i < OPTION_KEYS.size(); i++) {
            String o = r.get(OPTION_KEYS.get(i));
            if (o == null) {
                if (i < 2) errors.add("Option " + LETTERS.charAt(i) + " is required");
                gap = true;
                options.add(null);
            } else {
                if (gap && i >= 2) errors.add("Option " + LETTERS.charAt(i) + " is filled but an earlier option is empty");
                if (o.length() > 1000) errors.add("Option " + LETTERS.charAt(i) + " is too long (max 1000)");
                options.add(o);
            }
        }
        List<String> filled = options.stream().filter(Objects::nonNull).map(o -> o.toLowerCase(Locale.ROOT)).toList();
        if (new HashSet<>(filled).size() != filled.size()) errors.add("Options must be distinct");

        String answer = Text.trimToNull(r.get("correctanswer"));
        String letter = null;
        if (answer == null) {
            errors.add("Correct Answer is required (A, B, C or D)");
        } else {
            String a = answer.toUpperCase(Locale.ROOT).replace("OPTION", "").trim();
            if (a.length() != 1 || LETTERS.indexOf(a.charAt(0)) < 0) {
                errors.add("Correct Answer must be one letter: A, B, C or D");
            } else if (options.get(LETTERS.indexOf(a.charAt(0))) == null) {
                errors.add("Correct Answer " + a + " refers to an empty option");
            } else {
                letter = a;
            }
        }

        BigDecimal marks = null;
        String marksRaw = r.get("marks");
        if (marksRaw == null) {
            errors.add("Marks is required");
        } else {
            try {
                marks = new BigDecimal(marksRaw.trim());
                if (marks.signum() <= 0 || marks.compareTo(BigDecimal.valueOf(100)) > 0) {
                    errors.add("Marks must be greater than 0 and at most 100");
                } else if (marks.stripTrailingZeros().scale() > 2) {
                    errors.add("Marks can have at most 2 decimal places");
                }
            } catch (NumberFormatException e) {
                errors.add("Marks must be a number");
                marks = null;
            }
        }

        String explanation = r.get("explanation");
        if (explanation != null && explanation.length() > 4000) errors.add("Explanation is too long (max 4000)");

        return new QuestionImportRow(r.rowNumber(), text, options.stream().filter(Objects::nonNull).toList(),
                letter, marks, explanation, errors, warnings);
    }

    /** Builds a Question (without owner) from a valid row. */
    public Question toQuestion(QuestionImportRow row) {
        Question q = new Question();
        q.setText(row.question());
        q.setPoints(row.marks());
        q.setExplanation(row.explanation());
        int correct = LETTERS.indexOf(row.correctAnswer().charAt(0));
        for (int i = 0; i < row.options().size(); i++) {
            q.addOption(row.options().get(i), i == correct);
        }
        return q;
    }
}
