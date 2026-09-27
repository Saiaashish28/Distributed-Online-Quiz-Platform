package com.quizsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizsphere.service.QuizScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentAttemptIntegrationTest extends IntegrationTest {

    @Autowired
    QuizScheduler scheduler;

    private Map<String, Object> assignment(long quizId, String type, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("quizId", quizId, "name", "A-" + type, "type", type,
                "resultsReleaseMode", "IMMEDIATE"));
        body.putAll(extra);
        return body;
    }

    private static Map<String, Object> answer(long questionId, Long optionId, long seq) {
        Map<String, Object> m = new HashMap<>();
        m.put("questionId", questionId);
        m.put("optionId", optionId);
        m.put("seq", seq);
        return m;
    }

    /** Option ids by question id, in authoring order (index 0 is correct in publishedQuiz). */
    private Map<Long, List<Long>> optionsByQuestion(long quizId) throws Exception {
        Map<Long, List<Long>> out = new LinkedHashMap<>();
        for (JsonNode q : get("/api/admin/quizzes/" + quizId, adminToken).body().path("questions")) {
            List<Long> ids = new ArrayList<>();
            q.path("options").forEach(o -> ids.add(o.path("id").asLong()));
            out.put(q.path("id").asLong(), ids);
        }
        return out;
    }

    @Test
    void groupAndIndividualAssignmentsAreOnlyVisibleToTargets() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("22ECE001", 2, "ECE", "A", null);
        long outsider = createStudent("23MEC001", 1, "MECH", "A", null);
        long quiz = publishedQuiz(2);
        long group = dynamicGroup("III-CSE", Map.of("academicYears", List.of(3), "departments", List.of("CSE")), "ACADEMIC");
        long groupAssignment = createAssignment(assignment(quiz, "GROUP", Map.of("groupIds", List.of(group))));
        long individual = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("22ECE001"))));

        String cse = studentLogin("21CSE001");
        String ece = studentLogin("22ECE001");
        String mech = studentLogin("23MEC001");
        assertThat(ids(get("/api/student/assignments", cse).body())).containsExactly(groupAssignment);
        assertThat(ids(get("/api/student/assignments", ece).body())).containsExactly(individual);
        assertThat(get("/api/student/assignments", mech).body().size()).isZero();
        assertThat(get("/api/student/assignments/" + groupAssignment, mech).status()).isEqualTo(404);
        assertThat(post("/api/student/assignments/" + groupAssignment + "/start", mech, null).status()).isEqualTo(404);
        assertThat(outsider).isPositive();
    }

    @Test
    void joinCodeDoesNotBypassTargetingUnlessOpenAccess() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("22ECE001", 2, "ECE", "A", null);
        long quiz = publishedQuiz(1);
        long group = dynamicGroup("III-CSE", Map.of("academicYears", List.of(3)), "ACADEMIC");
        long restricted = createAssignment(assignment(quiz, "CODE", Map.of("groupIds", List.of(group))));
        String code = get("/api/admin/assignments/" + restricted, adminToken).body().path("summary").path("joinCode").asText();

        String cse = studentLogin("21CSE001");
        String ece = studentLogin("22ECE001");
        // not visible before joining, even for targeted students
        assertThat(get("/api/student/assignments", cse).body().size()).isZero();
        assertThat(post("/api/student/assignments/join", ece, Map.of("code", code)).status()).isEqualTo(403);
        assertThat(post("/api/student/assignments/join", cse, Map.of("code", code.toLowerCase())).status()).isEqualTo(200);
        assertThat(ids(get("/api/student/assignments", cse).body())).containsExactly(restricted);
        assertThat(post("/api/student/assignments/join", cse, Map.of("code", "ZZZZZZ")).status()).isEqualTo(404);

        long open = createAssignment(assignment(quiz, "CODE", Map.of("codeOpenAccess", true)));
        String openCode = get("/api/admin/assignments/" + open, adminToken).body().path("summary").path("joinCode").asText();
        assertThat(post("/api/student/assignments/join", ece, Map.of("code", openCode)).status()).isEqualTo(200);
        assertThat(post("/api/student/assignments/" + open + "/start", ece, null).status()).isEqualTo(200);
    }

    @Test
    void openAssignmentsReachAllActiveStudents() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long inactive = createStudent("21CSE002", 3, "CSE", "A", null);
        long quiz = publishedQuiz(1);
        long open = createAssignment(assignment(quiz, "OPEN", Map.of()));
        patch("/api/admin/students/" + inactive, adminToken, Map.of("active", false));
        assertThat(ids(get("/api/student/assignments", studentLogin("21CSE001")).body())).containsExactly(open);
        assertThat(get("/api/admin/assignments/" + open, adminToken).body().path("progress").path("eligible").asInt()).isEqualTo(1);
    }

    @Test
    void answersAreScoredOnServerAndSubmissionIsIdempotent() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long quiz = publishedQuiz(3); // points 1, 2, 3; correct = first option
        long a = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("21CSE001"))));
        String st = studentLogin("21CSE001");
        Res start = post("/api/student/assignments/" + a + "/start", st, null);
        assertThat(start.status()).isEqualTo(200);
        assertThat(start.body().toString()).doesNotContain("correct");
        long attempt = start.body().path("attemptId").asLong();

        // restarting resumes the same attempt
        assertThat(post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong()).isEqualTo(attempt);

        Map<Long, List<Long>> opts = optionsByQuestion(quiz);
        List<Long> qids = new ArrayList<>(opts.keySet());
        Res save = put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(
                answer(qids.get(0), opts.get(qids.get(0)).get(0), 1),     // correct, 1 pt
                answer(qids.get(1), opts.get(qids.get(1)).get(1), 1),     // wrong
                answer(qids.get(2), opts.get(qids.get(2)).get(1), 1))));  // wrong for now
        assertThat(save.status()).isEqualTo(200);
        // change answer 3 to correct (seq 2), then a delayed stale retry (seq 1) must not win
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(answer(qids.get(2), opts.get(qids.get(2)).get(0), 2))));
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(answer(qids.get(2), opts.get(qids.get(2)).get(1), 1))));
        assertThat(jdbc.queryForObject("select count(*) from answers where attempt_id = ?", Integer.class, attempt)).isEqualTo(3);

        // option from another question is rejected
        assertThat(put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers",
                List.of(answer(qids.get(0), opts.get(qids.get(1)).get(0), 5)))).status()).isEqualTo(400);

        // reconnect restores saved answers and the server deadline
        Res resumed = get("/api/student/attempts/" + attempt, st);
        assertThat(resumed.body().path("answers").size()).isEqualTo(3);
        assertThat(resumed.body().path("endsAt").asText()).isNotBlank();

        Res submit = post("/api/student/attempts/" + attempt + "/submit", st, null);
        assertThat(submit.body().path("status").asText()).isEqualTo("SUBMITTED");
        Res again = post("/api/student/attempts/" + attempt + "/submit", st, null);
        assertThat(again.body().path("submittedAt").asText()).isEqualTo(submit.body().path("submittedAt").asText());
        assertThat(jdbc.queryForObject("select count(*) from attempts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Double.class, attempt)).isEqualTo(4.0);

        // answers are frozen after submission
        assertThat(put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers",
                List.of(answer(qids.get(1), opts.get(qids.get(1)).get(0), 9)))).status()).isEqualTo(409);
        // attempt limit (default 1)
        assertThat(post("/api/student/assignments/" + a + "/start", st, null).status()).isEqualTo(409);

        Res result = get("/api/student/attempts/" + attempt + "/result", st);
        assertThat(result.body().path("score").asDouble()).isEqualTo(4.0);
        assertThat(result.body().path("correctCount").asInt()).isEqualTo(2);
    }

    @Test
    void concurrentSubmitsProduceOneFinalScore() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long quiz = publishedQuiz(2);
        long a = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("21CSE001"))));
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
        Map<Long, List<Long>> opts = optionsByQuestion(quiz);
        long q1 = opts.keySet().iterator().next();
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(answer(q1, opts.get(q1).get(0), 1))));

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Res>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> post("/api/student/attempts/" + attempt + "/submit", st, null)));
        }
        Set<String> submittedAts = new HashSet<>();
        for (Future<Res> f : futures) {
            Res r = f.get(30, TimeUnit.SECONDS);
            assertThat(r.status()).isEqualTo(200);
            submittedAts.add(r.body().path("submittedAt").asText());
        }
        pool.shutdown();
        assertThat(submittedAts).hasSize(1);
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Double.class, attempt)).isEqualTo(1.0);
    }

    @Test
    void multipleStudentsTakeQuizConcurrently() throws Exception {
        int n = 5;
        for (int i = 1; i <= n; i++) createStudent("21CSE00" + i, 3, "CSE", "A", null);
        long quiz = publishedQuiz(2);
        long group = dynamicGroup("III-CSE", Map.of("academicYears", List.of(3)), "ACADEMIC");
        long a = createAssignment(assignment(quiz, "GROUP", Map.of("groupIds", List.of(group))));
        Map<Long, List<Long>> opts = optionsByQuestion(quiz);
        List<Long> qids = new ArrayList<>(opts.keySet());

        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Double>> results = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            final int idx = i;
            results.add(pool.submit(() -> {
                String st = studentLogin("21CSE00" + idx);
                long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
                // odd students answer both correctly, even students only the first
                long second = idx % 2 == 1 ? opts.get(qids.get(1)).get(0) : opts.get(qids.get(1)).get(1);
                put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(
                        answer(qids.get(0), opts.get(qids.get(0)).get(0), 1), answer(qids.get(1), second, 1))));
                return post("/api/student/attempts/" + attempt + "/submit", st, null).status() == 200
                        ? get("/api/student/attempts/" + attempt + "/result", st).body().path("score").asDouble() : -1;
            }));
        }
        List<Double> scores = new ArrayList<>();
        for (Future<Double> f : results) scores.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(scores).containsExactlyInAnyOrder(3.0, 1.0, 3.0, 1.0, 3.0);
        Res results2 = get("/api/admin/assignments/" + a + "/results", adminToken);
        assertThat(results2.body().path("stats").path("submitted").asInt()).isEqualTo(n);
        for (JsonNode row : results2.body().path("rows")) {
            int idx = row.path("registerNumber").asText().charAt(7) - '0';
            assertThat(row.path("score").asDouble()).isEqualTo(idx % 2 == 1 ? 3.0 : 1.0);
        }
    }

    @Test
    void expiredAttemptsAreAutoSubmittedWithSavedAnswers() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long quiz = publishedQuiz(2);
        long a = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("21CSE001"))));
        String st = studentLogin("21CSE001");
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();
        Map<Long, List<Long>> opts = optionsByQuestion(quiz);
        long q1 = opts.keySet().iterator().next();
        put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers", List.of(answer(q1, opts.get(q1).get(0), 1))));

        // simulate the clock passing the attempt deadline
        jdbc.update("update attempts set ends_at = now() - interval '1 minute' where id = ?", attempt);
        assertThat(put("/api/student/attempts/" + attempt + "/answers", st, Map.of("answers",
                List.of(answer(q1, opts.get(q1).get(1), 2)))).status()).isEqualTo(409);
        scheduler.run();
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("AUTO_SUBMITTED");
        assertThat(jdbc.queryForObject("select score from attempts where id = ?", Double.class, attempt)).isEqualTo(1.0);
        assertThat(get("/api/student/assignments/" + a, st).body().path("status").asText()).isEqualTo("RESULTS_RELEASED");
    }

    @Test
    void deadlineAndAvailabilityWindowAreEnforced() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long quiz = publishedQuiz(1);
        String future = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        long upcoming = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("21CSE001"),
                "availableFrom", future)));
        String st = studentLogin("21CSE001");
        assertThat(get("/api/student/assignments/" + upcoming, st).body().path("status").asText()).isEqualTo("UPCOMING");
        assertThat(post("/api/student/assignments/" + upcoming + "/start", st, null).status()).isEqualTo(409);

        long past = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("21CSE001"))));
        jdbc.update("update assignments set deadline = now() - interval '1 minute' where id = ?", past);
        assertThat(get("/api/student/assignments/" + past, st).body().path("status").asText()).isEqualTo("EXPIRED");
        assertThat(post("/api/student/assignments/" + past + "/start", st, null).status()).isEqualTo(409);
    }

    @Test
    void liveSessionTransitionsAreServerControlled() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        long quiz = publishedQuiz(1);
        long a = createAssignment(assignment(quiz, "INDIVIDUAL", Map.of("registerNumbers", List.of("21CSE001"),
                "liveSession", true)));
        String st = studentLogin("21CSE001");
        assertThat(get("/api/admin/assignments/" + a, adminToken).body().path("summary").path("sessionState").asText()).isEqualTo("WAITING");
        assertThat(post("/api/student/assignments/" + a + "/start", st, null).status()).isEqualTo(409);

        assertThat(post("/api/admin/assignments/" + a + "/session/start", adminToken, null).status()).isEqualTo(200);
        assertThat(post("/api/admin/assignments/" + a + "/session/start", adminToken, null).status()).isEqualTo(409);
        long attempt = post("/api/student/assignments/" + a + "/start", st, null).body().path("attemptId").asLong();

        assertThat(post("/api/admin/assignments/" + a + "/session/end", adminToken, null).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from attempts where id = ?", String.class, attempt)).isEqualTo("AUTO_SUBMITTED");
        assertThat(post("/api/admin/assignments/" + a + "/session/start", adminToken, null).status()).isEqualTo(409);
    }

    @Test
    void concurrentSessionStartsOnlySucceedOnce() throws Exception {
        long quiz = publishedQuiz(1);
        long a = createAssignment(assignment(quiz, "OPEN", Map.of("liveSession", true)));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fs.add(pool.submit(() -> post("/api/admin/assignments/" + a + "/session/start", adminToken, null).status()));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : fs) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
    }

    @Test
    void resultsStayHiddenUntilReleaseAndStudentsSeeOnlyTheirOwn() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("21CSE002", 3, "CSE", "A", null);
        long quiz = publishedQuiz(1);
        long a = createAssignment(new HashMap<>(Map.of("quizId", quiz, "name", "Manual", "type", "INDIVIDUAL",
                "registerNumbers", List.of("21CSE001", "21CSE002"), "resultsReleaseMode", "MANUAL",
                "leaderboardEnabled", true)));
        String s1 = studentLogin("21CSE001");
        String s2 = studentLogin("21CSE002");
        long attempt = post("/api/student/assignments/" + a + "/start", s1, null).body().path("attemptId").asLong();
        Res beforeSubmit = get("/api/student/attempts/" + attempt + "/result", s1);
        assertThat(beforeSubmit.status()).isEqualTo(409);
        post("/api/student/attempts/" + attempt + "/submit", s1, null);

        Res hidden = get("/api/student/attempts/" + attempt + "/result", s1);
        assertThat(hidden.body().path("released").asBoolean()).isFalse();
        assertThat(hidden.body().has("score")).isFalse();
        assertThat(hidden.body().path("questions").size()).isZero();
        assertThat(get("/api/student/assignments/" + a + "/leaderboard", s1).status()).isEqualTo(403);
        // another student cannot read this attempt at all
        assertThat(get("/api/student/attempts/" + attempt + "/result", s2).status()).isEqualTo(404);
        assertThat(get("/api/student/attempts/" + attempt, s2).status()).isEqualTo(404);

        post("/api/admin/assignments/" + a + "/release-results", adminToken, null);
        Res released = get("/api/student/attempts/" + attempt + "/result", s1);
        assertThat(released.body().path("released").asBoolean()).isTrue();
        assertThat(released.body().path("score").asDouble()).isEqualTo(0.0);
        assertThat(released.body().path("questions").get(0).path("options").get(0).path("correct").asBoolean()).isTrue();
        Res board = get("/api/student/assignments/" + a + "/leaderboard", s1);
        assertThat(board.body().size()).isEqualTo(1);
        assertThat(board.body().get(0).path("me").asBoolean()).isTrue();
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path("id").asLong()));
        return out;
    }
}
