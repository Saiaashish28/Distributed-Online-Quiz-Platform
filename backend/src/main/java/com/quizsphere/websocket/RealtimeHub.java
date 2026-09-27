package com.quizsphere.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizsphere.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * In-memory registry of WebSocket connections per assignment channel.
 *
 * <p>This is correct for a single backend instance. With several instances, each only knows
 * its own connections, so events must be fanned out through shared messaging (e.g. Redis
 * Pub/Sub). Clients never depend on events alone: on (re)connect they receive an
 * authoritative snapshot built from the database.
 */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    public record Connection(WebSocketSession session, AuthUser user, Long assignmentId, String registerNumber,
                             String name, Instant connectedAt) {
        boolean isAdmin() {
            return user.isAdmin();
        }
    }

    public record Participant(Long studentId, String registerNumber, String name, Instant connectedAt) {
    }

    private final Map<Long, Set<Connection>> channels = new ConcurrentHashMap<>();
    private final Map<String, Connection> bySession = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RealtimeHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Registers a connection; returns true when this is the student's first connection to the channel. */
    public boolean register(Connection c) {
        unregister(c.session().getId());
        boolean first = c.user().studentId() != null && !isStudentConnected(c.assignmentId(), c.user().studentId());
        channels.computeIfAbsent(c.assignmentId(), k -> ConcurrentHashMap.newKeySet()).add(c);
        bySession.put(c.session().getId(), c);
        return first;
    }

    /** Removes a connection; returns it when this was the student's last connection to the channel. */
    public Optional<Connection> unregister(String sessionId) {
        Connection c = bySession.remove(sessionId);
        if (c == null) return Optional.empty();
        Set<Connection> set = channels.get(c.assignmentId());
        if (set != null) {
            set.remove(c);
            if (set.isEmpty()) channels.remove(c.assignmentId(), set);
        }
        if (c.user().studentId() != null && !isStudentConnected(c.assignmentId(), c.user().studentId())) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    public Optional<Connection> connection(String sessionId) {
        return Optional.ofNullable(bySession.get(sessionId));
    }

    private boolean isStudentConnected(Long assignmentId, Long studentId) {
        return channels.getOrDefault(assignmentId, Set.of()).stream()
                .anyMatch(c -> studentId.equals(c.user().studentId()));
    }

    public List<Participant> participants(Long assignmentId) {
        Map<Long, Participant> unique = new TreeMap<>();
        for (Connection c : channels.getOrDefault(assignmentId, Set.of())) {
            if (c.user().studentId() != null) {
                unique.merge(c.user().studentId(),
                        new Participant(c.user().studentId(), c.registerNumber(), c.name(), c.connectedAt()),
                        (a, b) -> a.connectedAt().isBefore(b.connectedAt()) ? a : b);
            }
        }
        return unique.values().stream().sorted(Comparator.comparing(Participant::registerNumber)).toList();
    }

    public int participantCount(Long assignmentId) {
        return participants(assignmentId).size();
    }

    // ---------------------------------------------------------------------- sending

    public void toAdmins(Long assignmentId, String type, Object data) {
        dispatch(assignmentId, type, data, Connection::isAdmin);
    }

    public void toStudents(Long assignmentId, String type, Object data) {
        dispatch(assignmentId, type, data, c -> !c.isAdmin());
    }

    public void toAll(Long assignmentId, String type, Object data) {
        dispatch(assignmentId, type, data, c -> true);
    }

    public void toStudent(Long assignmentId, Long studentId, String type, Object data) {
        dispatch(assignmentId, type, data, c -> studentId.equals(c.user().studentId()));
    }

    public void sendDirect(WebSocketSession session, Long assignmentId, String type, Object data) {
        send(session, envelope(assignmentId, type, data));
    }

    /** Sends after the current transaction commits, so clients never see uncommitted state. */
    private void dispatch(Long assignmentId, String type, Object data, Predicate<Connection> filter) {
        Runnable task = () -> {
            String json = envelope(assignmentId, type, data);
            for (Connection c : channels.getOrDefault(assignmentId, Set.of())) {
                if (filter.test(c)) send(c.session(), json);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private String envelope(Long assignmentId, String type, Object data) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", type);
        msg.put("assignmentId", assignmentId);
        msg.put("serverTime", Instant.now());
        msg.put("data", data);
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize realtime event", e);
        }
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping message to closed WebSocket {}", session.getId());
        }
    }
}
