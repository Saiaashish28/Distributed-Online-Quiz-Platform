package com.quizsphere;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest extends IntegrationTest {

    @LocalServerPort
    int port;

    /** Collects messages and the close status for one client connection. */
    class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session;

        Client connect(String token, long assignmentId) throws Exception {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setOrigin("http://localhost:5173");
            session = new StandardWebSocketClient().execute(this, headers, URI.create("ws://localhost:" + port + "/ws"))
                    .get(10, TimeUnit.SECONDS);
            session.sendMessage(new TextMessage(json.writeValueAsString(
                    Map.of("type", "subscribe", "token", token, "assignmentId", assignmentId))));
            return this;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            messages.add(json.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closed.complete(status);
        }

        JsonNode await(String type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                JsonNode m = messages.poll(500, TimeUnit.MILLISECONDS);
                if (m != null && type.equals(m.path("type").asText())) return m;
            }
            throw new AssertionError("Did not receive " + type);
        }
    }

    @Test
    void snapshotsEventsAndAuthorization() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("22ECE001", 2, "ECE", "A", null);
        long quiz = publishedQuiz(1);
        long a = createAssignment(new java.util.HashMap<>(Map.of("quizId", quiz, "name", "Live", "type", "INDIVIDUAL",
                "registerNumbers", List.of("21CSE001"), "resultsReleaseMode", "MANUAL", "liveSession", true)));

        Client admin = new Client().connect(adminToken, a);
        JsonNode adminSnap = admin.await("session_snapshot");
        assertThat(adminSnap.path("data").path("sessionState").asText()).isEqualTo("WAITING");
        assertThat(adminSnap.path("data").path("progress").path("eligible").asInt()).isEqualTo(1);

        String studentToken = studentLogin("21CSE001");
        Client student = new Client().connect(studentToken, a);
        JsonNode studentSnap = student.await("session_snapshot");
        assertThat(studentSnap.path("data").has("participants")).isFalse(); // admin-only data
        JsonNode joined = admin.await("participant_joined");
        assertThat(joined.path("data").path("registerNumber").asText()).isEqualTo("21CSE001");

        post("/api/admin/assignments/" + a + "/session/start", adminToken, null);
        assertThat(student.await("session_started").path("data").path("sessionState").asText()).isEqualTo("LIVE");

        long attempt = post("/api/student/assignments/" + a + "/start", studentToken, null).body().path("attemptId").asLong();
        assertThat(admin.await("submission_progress").path("data").path("inProgress").asInt()).isEqualTo(1);

        // reconnecting gets an authoritative snapshot including the running attempt
        student.session.close();
        Client again = new Client().connect(studentToken, a);
        JsonNode snap = again.await("session_snapshot");
        assertThat(snap.path("data").path("sessionState").asText()).isEqualTo("LIVE");
        assertThat(snap.path("data").path("attempt").path("id").asLong()).isEqualTo(attempt);

        post("/api/admin/assignments/" + a + "/release-results", adminToken, null);
        assertThat(again.await("results_released")).isNotNull();

        // bad token and ineligible student are disconnected
        Client bad = new Client().connect("not-a-token", a);
        assertThat(bad.closed.get(10, TimeUnit.SECONDS).getCode()).isEqualTo(4401);
        Client ineligible = new Client().connect(studentLogin("22ECE001"), a);
        assertThat(ineligible.closed.get(10, TimeUnit.SECONDS).getCode()).isEqualTo(4403);

        admin.session.close();
        again.session.close();
    }
}
