package com.quizsphere;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExportProctoringIntegrationTest extends IntegrationTest {

    private long setupAssignment(boolean proctoring) throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("21CSE002", 3, "CSE", "B", null);
        long quiz = publishedQuiz(2);
        long group = dynamicGroup("III-CSE", Map.of("academicYears", List.of(3)), "ACADEMIC");
        Map<String, Object> body = new HashMap<>(Map.of("quizId", quiz, "name", "Test 1", "type", "GROUP",
                "groupIds", List.of(group), "resultsReleaseMode", "MANUAL"));
        if (proctoring) {
            body.put("proctoring", Map.of("enabled", true, "warningThreshold", 2, "showWarnings", true,
                    "flagForReview", true, "requireFullscreen", false));
        }
        return createAssignment(body);
    }

    private Map<String, Row> rowsByRegister(Sheet sheet) {
        DataFormatter f = new DataFormatter();
        Map<String, Row> out = new HashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row r = sheet.getRow(i);
            out.put(f.formatCellValue(r.getCell(0)), r);
        }
        return out;
    }

    @Test
    void marksheetIncludesEveryAssignedStudentWithCorrectMarks() throws Exception {
        long a = setupAssignment(false);
        String st = studentLogin("21CSE001");
        JsonNode start = post("/api/student/assignments/" + a + "/start", st, null).body();
        long attempt = start.path("attemptId").asLong();
        JsonNode q1 = start.path("questions").get(0);
        // publishedQuiz: first option of each question is correct; Q1 is worth 1 point
        long correctOption = get("/api/admin/quizzes/" + get("/api/admin/assignments/" + a, adminToken)
                .body().path("summary").path("quizId").asLong(), adminToken)
                .body().path("questions").get(0).path("options").get(0).path("id").asLong();
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers",
                List.of(Map.of("questionId", q1.path("id").asLong(), "optionId", correctOption, "seq", 1))));
        post("/api/student/attempts/" + attempt + "/submit", st, null);

        Res file = get("/api/admin/assignments/" + a + "/export/marksheet?nonSubmitterMarks=ZERO&tz=Asia/Kolkata", adminToken);
        assertThat(file.status()).isEqualTo(200);
        try (XSSFWorkbook wb = readXlsx(file.raw())) {
            assertThat(wb.getSheetName(0)).isEqualTo("Marksheet");
            assertThat(wb.getSheet("Detailed Responses")).isNotNull();
            assertThat(wb.getSheet("Summary")).isNotNull();
            Sheet marks = wb.getSheet("Marksheet");
            assertThat(marks.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Register Number");
            assertThat(marks.getPaneInformation().isFreezePane()).isTrue();
            Map<String, Row> rows = rowsByRegister(marks);
            assertThat(rows).containsOnlyKeys("21CSE001", "21CSE002");
            Row submitted = rows.get("21CSE001");
            assertThat(submitted.getCell(9).getNumericCellValue()).isEqualTo(1.0);
            assertThat(submitted.getCell(8).getNumericCellValue()).isEqualTo(3.0);
            assertThat(submitted.getCell(12).getStringCellValue()).isEqualTo("SUBMITTED");
            Row missing = rows.get("21CSE002");
            assertThat(missing.getCell(12).getStringCellValue()).isEqualTo("NOT_SUBMITTED");
            assertThat(missing.getCell(9).getNumericCellValue()).isEqualTo(0.0);

            Sheet responses = wb.getSheet("Detailed Responses");
            assertThat(responses.getLastRowNum()).isEqualTo(2); // 2 questions for the one attempt
        }

        // blank marks for non-submitters when requested
        Res blank = get("/api/admin/assignments/" + a + "/export/marksheet?nonSubmitterMarks=BLANK", adminToken);
        try (XSSFWorkbook wb = readXlsx(blank.raw())) {
            assertThat(rowsByRegister(wb.getSheet("Marksheet")).get("21CSE002").getCell(9)).isNull();
        }
    }

    @Test
    void exportsAreAdminOwnerOnly() throws Exception {
        long a = setupAssignment(false);
        String st = studentLogin("21CSE001");
        assertThat(get("/api/admin/assignments/" + a + "/export/marksheet", st).status()).isEqualTo(403);
        assertThat(get("/api/admin/assignments/" + a + "/export/marksheet", null).status()).isEqualTo(401);
        String other = registerAdmin("other@test.edu", "Other Password1");
        assertThat(get("/api/admin/assignments/" + a + "/export/marksheet", other).status()).isEqualTo(404);
    }

    @Test
    void spreadsheetFormulaInjectionIsNeutralized() throws Exception {
        long id = createStudent("21CSE009", 3, "CSE", "A", null);
        patch("/api/admin/students/" + id, adminToken, Map.of("fullName", "=HYPERLINK(\"http://evil\",\"x\")"));
        long quiz = publishedQuiz(1);
        long a = createAssignment(new HashMap<>(Map.of("quizId", quiz, "name", "@SUM(A1)", "type", "OPEN",
                "resultsReleaseMode", "IMMEDIATE")));
        Res file = get("/api/admin/assignments/" + a + "/export/marksheet", adminToken);
        try (XSSFWorkbook wb = readXlsx(file.raw())) {
            Row row = wb.getSheet("Marksheet").getRow(1);
            assertThat(row.getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(row.getCell(1).getStringCellValue()).startsWith("'=");
            assertThat(row.getCell(7).getStringCellValue()).startsWith("'@");
        }
    }

    @Test
    void consolidatedAndQuizExports() throws Exception {
        long a = setupAssignment(false);
        long quizId = get("/api/admin/assignments/" + a, adminToken).body().path("summary").path("quizId").asLong();
        Res consolidated = get("/api/admin/exports/consolidated?assignmentIds=" + a, adminToken);
        assertThat(consolidated.status()).isEqualTo(200);
        try (XSSFWorkbook wb = readXlsx(consolidated.raw())) {
            assertThat(wb.getSheet("Marksheet").getLastRowNum()).isEqualTo(2);
        }
        Res quiz = get("/api/admin/quizzes/" + quizId + "/export/marksheet", adminToken);
        assertThat(quiz.status()).isEqualTo(200);
        Res responses = get("/api/admin/assignments/" + a + "/export/responses", adminToken);
        try (XSSFWorkbook wb = readXlsx(responses.raw())) {
            assertThat(wb.getSheet("Detailed Responses")).isNotNull();
        }
    }

    @Test
    void proctoringEventsAreRecordedFlaggedAndReviewedWithoutChangingMarks() throws Exception {
        long a = setupAssignment(true);
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();

        Res first = post("/api/student/attempts/" + attempt + "/proctoring-events", st,
                Map.of("eventType", "FOCUS_LOST", "metadata", Map.of("visibilityState", "hidden", "userAgent", "secret")));
        assertThat(first.body().path("warningCount").asInt()).isEqualTo(1);
        assertThat(first.body().path("showWarning").asBoolean()).isTrue();
        assertThat(first.body().path("flagged").asBoolean()).isFalse();
        post("/api/student/attempts/" + attempt + "/proctoring-events", st, Map.of("eventType", "FOCUS_RETURNED",
                "metadata", Map.of("awayMs", 4200)));
        Res second = post("/api/student/attempts/" + attempt + "/proctoring-events", st, Map.of("eventType", "FOCUS_LOST"));
        assertThat(second.body().path("flagged").asBoolean()).isTrue();

        // other students cannot post events to this attempt
        String other = studentLogin("21CSE002");
        assertThat(post("/api/student/attempts/" + attempt + "/proctoring-events", other,
                Map.of("eventType", "FOCUS_LOST")).status()).isEqualTo(404);
        // students cannot read the admin timeline
        assertThat(get("/api/admin/assignments/" + a + "/proctoring-events", st).status()).isEqualTo(403);

        Res timeline = get("/api/admin/assignments/" + a + "/proctoring-events", adminToken);
        JsonNode at = timeline.body().path("attempts").get(0);
        assertThat(at.path("registerNumber").asText()).isEqualTo("21CSE001");
        assertThat(at.path("warningCount").asInt()).isEqualTo(2);
        assertThat(at.path("flagged").asBoolean()).isTrue();
        assertThat(at.path("events").size()).isEqualTo(3);
        assertThat(at.path("events").get(0).path("metadata").has("userAgent")).isFalse();

        long eventId = at.path("events").get(0).path("id").asLong();
        Res review = patch("/api/admin/proctoring-events/" + eventId + "/review", adminToken,
                Map.of("reviewStatus", "FOLLOW_UP_REQUIRED", "notes", "Discuss with student"));
        assertThat(review.body().path("reviewStatus").asText()).isEqualTo("FOLLOW_UP_REQUIRED");
        assertThat(review.body().path("reviewedBy").asText()).isEqualTo("Admin");
        Res bulk = patch("/api/admin/attempts/" + attempt + "/proctoring-review", adminToken, Map.of("reviewStatus", "REVIEWED"));
        assertThat(bulk.body().path("updated").asInt()).isEqualTo(3);

        post("/api/student/attempts/" + attempt + "/submit", st, null);
        // proctoring signals never change marks automatically
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Double.class, attempt)).isEqualTo(0.0);
        assertThat(post("/api/student/attempts/" + attempt + "/proctoring-events", st,
                Map.of("eventType", "FOCUS_LOST")).status()).isEqualTo(409);
    }

    @Test
    void proctoringIsRejectedWhenDisabled() throws Exception {
        long a = setupAssignment(false);
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
        assertThat(post("/api/student/attempts/" + attempt + "/proctoring-events", st,
                Map.of("eventType", "FOCUS_LOST")).status()).isEqualTo(400);
    }
}
