package com.quizsphere.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizsphere.security.AuthUser;
import com.quizsphere.security.TokenAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Protocol: the client connects to /ws and sends
 * {@code {"type":"subscribe","token":"<jwt>","assignmentId":123}} as its first message
 * (keeping the token out of URLs and logs). The server replies with {@code session_snapshot}
 * and then pushes events. {@code {"type":"ping"}} is answered with {@code pong}.
 */
@Component
public class QuizSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuizSocketHandler.class);
    private static final long AUTH_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final TokenAuthenticator authenticator;
    private final SessionSnapshotService snapshots;
    private final RealtimeHub hub;
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-auth-timeout");
        t.setDaemon(true);
        return t;
    });

    public QuizSocketHandler(ObjectMapper objectMapper, TokenAuthenticator authenticator,
                             SessionSnapshotService snapshots, RealtimeHub hub) {
        this.objectMapper = objectMapper;
        this.authenticator = authenticator;
        this.snapshots = snapshots;
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 256 * 1024);
        decorated.put(session.getId(), safe);
        timer.schedule(() -> {
            if (session.isOpen() && hub.connection(session.getId()).isEmpty()) {
                close(session, CloseStatus.POLICY_VIOLATION.withReason("Authentication timeout"));
            }
        }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketSession safe = decorated.getOrDefault(session.getId(), session);
        if (message.getPayloadLength() > 4096) {
            close(session, CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        JsonNode msg;
        try {
            msg = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            hub.sendDirect(safe, null, "error", Map.of("message", "Malformed message"));
            return;
        }
        String type = msg.path("type").asText("");
        switch (type) {
            case "subscribe" -> subscribe(session, safe, msg);
            case "ping" -> hub.sendDirect(safe, null, "pong", Map.of("serverTime", Instant.now()));
            case "snapshot" -> hub.connection(session.getId()).ifPresent(c ->
                    hub.sendDirect(safe, c.assignmentId(), "session_snapshot", snapshots.snapshot(c.user(), c.assignmentId())));
            default -> hub.sendDirect(safe, null, "error", Map.of("message", "Unknown message type"));
        }
    }

    private void subscribe(WebSocketSession session, WebSocketSession safe, JsonNode msg) {
        Optional<AuthUser> user = authenticator.authenticate(msg.path("token").asText(null));
        long assignmentId = msg.path("assignmentId").asLong(-1);
        if (user.isEmpty()) {
            close(session, new CloseStatus(4401, "Unauthorized"));
            return;
        }
        Optional<SessionSnapshotService.Access> access = assignmentId > 0
                ? snapshots.authorize(user.get(), assignmentId) : Optional.empty();
        if (access.isEmpty()) {
            close(session, new CloseStatus(4403, "Forbidden"));
            return;
        }
        boolean first = hub.register(new RealtimeHub.Connection(safe, user.get(), assignmentId,
                access.get().registerNumber(), access.get().name(), Instant.now()));
        hub.sendDirect(safe, assignmentId, "session_snapshot", snapshots.snapshot(user.get(), assignmentId));
        if (first) {
            hub.toAdmins(assignmentId, "participant_joined", Map.of("studentId", user.get().studentId(),
                    "registerNumber", access.get().registerNumber(), "name", access.get().name()));
        }
        broadcastCount(assignmentId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        decorated.remove(session.getId());
        Optional<RealtimeHub.Connection> conn = hub.connection(session.getId());
        Optional<RealtimeHub.Connection> left = hub.unregister(session.getId());
        left.ifPresent(c -> hub.toAdmins(c.assignmentId(), "participant_left", Map.of("studentId", c.user().studentId(),
                "registerNumber", c.registerNumber())));
        conn.ifPresent(c -> broadcastCount(c.assignmentId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket transport error on {}: {}", session.getId(), exception.getMessage());
    }

    private void broadcastCount(Long assignmentId) {
        hub.toAll(assignmentId, "participant_count_updated", Map.of("count", hub.participantCount(assignmentId)));
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // already closed
        }
    }
}
