package com.quizsphere;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAuthoringIntegrationTest extends IntegrationTest {

    private static final List<Object> HEADER = List.of("Question", "Option A", "Option B", "Option C", "Option D",
            "Correct Answer", "Marks", "Explanation");

    private long draftQuiz() throws Exception {
        return post("/api/admin/quizzes", adminToken, Map.of("title", "Draft", "durationMinutes", 20))
                .body().path("id").asLong();
    }

    @Test
    void publishRequiresValidQuestions() throws Exception {
        long quiz = draftQuiz();
        Res empty = post("/api/admin/quizzes/" + quiz + "/publish", adminToken, null);
        assertThat(empty.status()).isEqualTo(422);

        Res twoCorrect = post("/api/admin/quizzes/" + quiz + "/questions", adminToken, Map.of("text", "Q", "points", 1,
                "options", List.of(Map.of("text", "a", "correct", true), Map.of("text", "b", "correct", true))));
        assertThat(twoCorrect.status()).isEqualTo(400);

        post("/api/admin/quizzes/" + quiz + "/questions", adminToken, question("Q1", 1, 2));
        Res published = post("/api/admin/quizzes/" + quiz + "/publish", adminToken, null);
        assertThat(published.body().path("status").asText()).isEqualTo("PUBLISHED");

        // published quizzes are locked
        assertThat(post("/api/admin/quizzes/" + quiz + "/questions", adminToken, question("Q2", 0, 1)).status()).isEqualTo(409);
    }

    @Test
    void questionImportReportsInvalidRowsAndNeedsConfirmation() throws Exception {
        long quiz = draftQuiz();
        byte[] file = xlsx(List.of(HEADER,
                List.of("What is JVM?", "Java Virtual Machine", "Java Variable Method", "Java Visual Mode", "Java Version Manager", "A", 1, "Optional"),
                List.of("Which protocol is connection-oriented?", "UDP", "TCP", "HTTP", "DNS", "B", 2, ""),
                List.of("No answer", "x", "y", "", "", "C", 1, ""),
                List.of("Bad marks", "x", "y", "z", "w", "A", "two", ""),
                List.of("Dup options", "x", "X", "", "", "A", 1, "")));
        Res preview = upload("/api/admin/quizzes/" + quiz + "/questions/import", adminToken, "q.xlsx", file);
        assertThat(preview.body().path("validRows").asInt()).isEqualTo(2);
        assertThat(preview.body().path("invalidRows").asInt()).isEqualTo(3);
        assertThat(preview.body().path("rows").get(2).path("errors").toString()).contains("empty option");
        assertThat(preview.body().path("rows").get(3).path("errors").toString()).contains("Marks must be a number");
        assertThat(preview.body().path("rows").get(4).path("errors").toString()).contains("distinct");
        assertThat(jdbc.queryForObject("select count(*) from questions", Integer.class)).isZero();

        assertThat(upload("/api/admin/quizzes/" + quiz + "/questions/import?mode=commit", adminToken, "q.xlsx", file)
                .status()).isEqualTo(422);
        Res commit = upload("/api/admin/quizzes/" + quiz + "/questions/import?mode=commit&skipInvalid=true",
                adminToken, "q.xlsx", file);
        assertThat(commit.body().path("imported").asInt()).isEqualTo(2);
        Res detail = get("/api/admin/quizzes/" + quiz, adminToken);
        assertThat(detail.body().path("totalPoints").asDouble()).isEqualTo(3.0);
        assertThat(detail.body().path("questions").get(1).path("options").get(1).path("correct").asBoolean()).isTrue();
    }

    @Test
    void importRejectsMissingColumnsAndWrongFileTypes() throws Exception {
        long quiz = draftQuiz();
        Res missing = upload("/api/admin/quizzes/" + quiz + "/questions/import", adminToken, "q.csv",
                "Question,Option A\nx,y\n".getBytes());
        assertThat(missing.body().path("headerErrors").toString()).contains("Option B").contains("Correct Answer");
        assertThat(upload("/api/admin/quizzes/" + quiz + "/questions/import", adminToken, "q.xlsx",
                "not a zip".getBytes()).status()).isEqualTo(400);
    }

    @Test
    void bankCopiesAreIndependentOfLaterBankEdits() throws Exception {
        long bank = post("/api/admin/question-banks", adminToken, Map.of("name", "DCC bank")).body().path("id").asLong();
        long bq = post("/api/admin/question-banks/" + bank + "/questions", adminToken, question("Original", 0, 1))
                .body().path("id").asLong();
        long quiz = draftQuiz();
        Res copy = post("/api/admin/question-banks/" + bank + "/copy-to-quiz", adminToken,
                Map.of("quizId", quiz, "questionIds", List.of(bq)));
        assertThat(copy.body().path("copied").asInt()).isEqualTo(1);
        post("/api/admin/quizzes/" + quiz + "/publish", adminToken, null);

        patch("/api/admin/questions/" + bq, adminToken, question("Edited in bank", 2, 5));
        Res detail = get("/api/admin/quizzes/" + quiz, adminToken);
        assertThat(detail.body().path("questions").get(0).path("text").asText()).isEqualTo("Original");
        assertThat(detail.body().path("questions").get(0).path("sourceQuestionId").asLong()).isEqualTo(bq);
    }

    @Test
    void adminsCannotSeeEachOthersQuizzes() throws Exception {
        long quiz = draftQuiz();
        String other = registerAdmin("other@test.edu", "Other Password1");
        assertThat(get("/api/admin/quizzes/" + quiz, other).status()).isEqualTo(404);
        assertThat(get("/api/admin/quizzes", other).body().size()).isZero();
    }
}
