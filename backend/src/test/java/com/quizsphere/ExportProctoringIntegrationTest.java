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

    @org.springframework.beans.factory.annotation.Autowired
    com.quizsphere.service.QuizScheduler scheduler;

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
    void thirdWarningAutoSubmitsWithSavedAnswers() throws Exception {
        long a = setupAssignment(true);
        String st = studentLogin("21CSE001");
        JsonNode start = post("/api/student/assignments/" + a + "/start", st, null).body();
        long attempt = start.path("attemptId").asLong();
        long quizId = get("/api/admin/assignments/" + a, adminToken).body().path("summary").path("quizId").asLong();
        JsonNode q1 = get("/api/admin/quizzes/" + quizId, adminToken).body().path("questions").get(0);
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(Map.of(
                "questionId", q1.path("id").asLong(), "optionId", q1.path("options").get(0).path("id").asLong(), "seq", 1))));
        String url = "/api/student/attempts/" + attempt + "/proctoring-events";

        // Returning to the page never counts toward the limit.
        post(url, st, Map.of("eventType", "FOCUS_LOST"));
        post(url, st, Map.of("eventType", "FOCUS_RETURNED"));
        ageEvents(attempt); // separate actions, not one tab switch
        Res second = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT"));
        assertThat(second.body().path("autoSubmitted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("IN_PROGRESS");

        ageEvents(attempt);
        Res third = post(url, st, Map.of("eventType", "FOCUS_LOST"));
        assertThat(third.body().path("autoSubmitted").asBoolean()).isTrue();
        assertThat(third.body().path("warningCount").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("AUTO_SUBMITTED");
        // saved answer (question 1, correct, 1 mark) is graded
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Double.class, attempt)).isEqualTo(1.0);

        assertThat(post(url, st, Map.of("eventType", "FOCUS_LOST")).status()).isEqualTo(409);
        assertThat(put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(Map.of(
                "questionId", q1.path("id").asLong(), "optionId", q1.path("options").get(1).path("id").asLong(), "seq", 2))))
                .status()).isEqualTo(409);
    }

    private long fullscreenAssignment() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long quiz = publishedQuiz(2);
        return createAssignment(new HashMap<>(Map.of("quizId", quiz, "name", "FS", "type", "INDIVIDUAL",
                "registerNumbers", List.of("21CSE001"), "resultsReleaseMode", "MANUAL",
                "proctoring", Map.of("enabled", true, "warningThreshold", 3, "showWarnings", true,
                        "flagForReview", true, "requireFullscreen", true))));
    }

    @Test
    void fullscreenCountdownStartsOnExitAndIsCancelledOnReturn() throws Exception {
        long a = fullscreenAssignment();
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
        String url = "/api/student/attempts/" + attempt + "/proctoring-events";

        Res exit = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT"));
        assertThat(exit.body().path("fullscreenDeadline").asText()).isNotBlank();
        assertThat(get("/api/student/attempts/" + attempt, st).body().path("fullscreenDeadline").asText()).isNotBlank();

        Res back = post(url, st, Map.of("eventType", "FULLSCREEN_ENTER"));
        assertThat(back.body().has("fullscreenDeadline")).isFalse();
        assertThat(jdbc.queryForObject("select fullscreen_exited_at is null from attempts where id = ?", Boolean.class, attempt)).isTrue();

        // Loading the page outside fullscreen starts a countdown but is not a page-leave warning,
        // and a second report (e.g. another reload) keeps the original start time.
        Res onLoad = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT", "metadata", Map.of("reason", "page_load")));
        assertThat(onLoad.body().path("warningCount").asInt()).isEqualTo(1); // the real exit counted; the page load did not
        String firstDeadline = onLoad.body().path("fullscreenDeadline").asText();
        Thread.sleep(50);
        Res again = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT", "metadata", Map.of("reason", "page_load")));
        assertThat(again.body().path("fullscreenDeadline").asText()).isEqualTo(firstDeadline);
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("IN_PROGRESS");
    }

    /** Moves recorded events into the past so the next event is a separate action. */
    private void ageEvents(long attempt) {
        jdbc.update("update proctoring_events set occurred_at = occurred_at - interval '1 minute' where attempt_id = ?", attempt);
    }

    @Test
    void eachFullscreenExitIsAWarningAndTheThirdAutoSubmits() throws Exception {
        long a = fullscreenAssignment();
        String st = studentLogin("21CSE001");
        JsonNode start = post("/api/student/assignments/" + a + "/start", st, null).body();
        long attempt = start.path("attemptId").asLong();
        long quizId = get("/api/admin/assignments/" + a, adminToken).body().path("summary").path("quizId").asLong();
        JsonNode q1 = get("/api/admin/quizzes/" + quizId, adminToken).body().path("questions").get(0);
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(Map.of(
                "questionId", q1.path("id").asLong(), "optionId", q1.path("options").get(0).path("id").asLong(), "seq", 1))));
        String url = "/api/student/attempts/" + attempt + "/proctoring-events";

        for (int i = 1; i <= 2; i++) {
            Res exit = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT"));
            assertThat(exit.body().path("warningCount").asInt()).isEqualTo(i);
            assertThat(exit.body().path("autoSubmitted").asBoolean()).isFalse();
            assertThat(exit.body().path("fullscreenDeadline").asText()).isNotBlank(); // countdown running
            assertThat(exit.body().path("message").asText()).contains(i + " of 3").contains("10 seconds");
            post(url, st, Map.of("eventType", "FULLSCREEN_ENTER"));
            ageEvents(attempt);
        }
        Res third = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT"));
        assertThat(third.body().path("autoSubmitted").asBoolean()).isTrue();
        assertThat(third.body().path("reason").asText()).isEqualTo("PROCTORING");
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("AUTO_SUBMITTED");
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Double.class, attempt)).isEqualTo(1.0);
    }

    @Test
    void tabSwitchThatAlsoExitsFullscreenCountsOnce() throws Exception {
        long a = fullscreenAssignment();
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
        String url = "/api/student/attempts/" + attempt + "/proctoring-events";
        post(url, st, Map.of("eventType", "FOCUS_LOST"));
        Res exit = post(url, st, Map.of("eventType", "FULLSCREEN_EXIT"));
        assertThat(exit.body().path("warningCount").asInt()).isEqualTo(1);
        assertThat(exit.body().path("fullscreenDeadline").asText()).isNotBlank(); // countdown still starts
        assertThat(jdbc.queryForObject("select count(*) from proctoring_events where attempt_id = ?", Integer.class, attempt)).isEqualTo(2);
    }

    @Test
    void expiredFullscreenCountdownAutoSubmitsOnNextRequestOrBySchedule() throws Exception {
        long a = fullscreenAssignment();
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
        String url = "/api/student/attempts/" + attempt + "/proctoring-events";
        post(url, st, Map.of("eventType", "FULLSCREEN_EXIT"));
        jdbc.update("update attempts set fullscreen_exited_at = now() - interval '1 minute' where id = ?", attempt);

        // Returning too late does not help: the server submits instead.
        Res late = post(url, st, Map.of("eventType", "FULLSCREEN_ENTER"));
        assertThat(late.body().path("autoSubmitted").asBoolean()).isTrue();
        assertThat(late.body().path("reason").asText()).isEqualTo("FULLSCREEN");
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("AUTO_SUBMITTED");

        // Scheduler backstop when the browser goes silent.
        createStudent("21CSE002", 3, "CSE", "A", null);
        patch("/api/admin/assignments/" + a, adminToken, Map.of("registerNumbers", List.of("21CSE001", "21CSE002")));
        String st2 = studentLogin("21CSE002");
        long attempt2 = post("/api/student/assignments/" + a + "/start", st2, null).body().path("attemptId").asLong();
        post("/api/student/attempts/" + attempt2 + "/proctoring-events", st2, Map.of("eventType", "FULLSCREEN_EXIT"));
        jdbc.update("update attempts set fullscreen_exited_at = now() - interval '1 minute' where id = ?", attempt2);
        scheduler.run();
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt2)).isEqualTo("AUTO_SUBMITTED");
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
