package com.quizsphere.service;

import com.quizsphere.config.AppProperties;
import com.quizsphere.dto.ProctoringDtos.*;
import com.quizsphere.dto.StudentQuizDtos.ProctoringEventRequest;
import com.quizsphere.dto.StudentQuizDtos.ProctoringEventResponse;
import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.AttemptRepository;
import com.quizsphere.repository.ProctoringEventRepository;
import com.quizsphere.repository.UserRepository;
import com.quizsphere.security.AuthUser;
import com.quizsphere.util.Text;
import com.quizsphere.websocket.RealtimeHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Browser proctoring signals. Events are signals for human review, not proof of misconduct:
 * nothing here changes marks or disqualifies anyone.
 */
@Service
public class ProctoringService {

    private static final int MAX_EVENTS_PER_ATTEMPT = 1000;

    /** Warning events (focus lost / fullscreen exit) after which a monitored attempt is submitted automatically. */
    public static final int AUTO_SUBMIT_WARNINGS = 3;

    /** Seconds a student has to return to required fullscreen before the attempt is submitted. */
    public static final int FULLSCREEN_RETURN_SECONDS = 10;

    /** A focus loss and a fullscreen exit this close together are one action and one warning. */
    static final int LEAVE_MERGE_SECONDS = 3;

    private final AttemptRepository attemptRepository;
    private final ProctoringEventRepository eventRepository;
    private final UserRepository userRepository;
    private final AssignmentService assignmentService;
    private final AttemptService attemptService;
    private final RealtimeHub hub;
    private final Duration grace;

    public ProctoringService(AttemptRepository attemptRepository, ProctoringEventRepository eventRepository,
                             UserRepository userRepository, AssignmentService assignmentService,
                             AttemptService attemptService, RealtimeHub hub, AppProperties props) {
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
        this.hub = hub;
        this.grace = Duration.ofSeconds(props.quiz().graceSeconds());
    }

    @Transactional
    public ProctoringEventResponse record(Long attemptId, Long studentId, ProctoringEventRequest req) {
        Attempt at = attemptRepository.lockById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        if (!at.getStudent().getId().equals(studentId)) {
            throw ApiException.notFound("Attempt");
        }
        Assignment a = at.getAssignment();
        if (!a.isProctoringEnabled()) {
            throw ApiException.badRequest("Monitoring is not enabled for this quiz");
        }
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        boolean running = at.getStatus() == AttemptStatus.IN_PROGRESS && now.isBefore(at.getEndsAt().plus(grace));
        if (!running) {
            throw ApiException.conflict("This attempt is no longer in progress");
        }
        int threshold = a.getProctoringWarningThreshold();
        // An expired return-to-fullscreen countdown wins over whatever this event says.
        if (attemptService.enforceFullscreen(at, now)) {
            return response(a, at, false, "FULLSCREEN");
        }
        if (eventRepository.countByAttemptId(attemptId) >= MAX_EVENTS_PER_ATTEMPT) {
            return response(a, at, false, null);
        }
        Map<String, Object> metadata = sanitize(req.metadata());
        // A page that loads outside fullscreen (e.g. after a reload) starts or continues the
        // countdown but is not counted as a page-leave warning.
        boolean onLoad = metadata != null && "page_load".equals(metadata.get("reason"));
        if (a.isProctoringRequireFullscreen()) {
            if (req.eventType() == ProctoringEventType.FULLSCREEN_EXIT && at.getFullscreenExitedAt() == null) {
                at.setFullscreenExitedAt(now); // a reload during a countdown keeps the original start
            } else if (req.eventType() == ProctoringEventType.FULLSCREEN_ENTER) {
                at.setFullscreenExitedAt(null);
            }
        }
        // Every fullscreen exit and every page-leave is a warning. One action counts once: switching
        // tabs while in fullscreen fires both events, so the second within a few seconds is merged.
        boolean warning = req.eventType().isWarning() && !onLoad && !mergedWithRecentLeave(at, req.eventType(), now);
        boolean autoSubmit = false;
        if (warning) {
            at.setProctoringWarningCount(at.getProctoringWarningCount() + 1);
            if (a.isProctoringFlagForReview() && at.getProctoringWarningCount() >= threshold) {
                at.setProctoringFlagged(true);
            }
            if (at.getProctoringWarningCount() >= AUTO_SUBMIT_WARNINGS) {
                autoSubmit = true;
                at.setProctoringFlagged(true);
            }
        }
        ProctoringEvent e = new ProctoringEvent();
        e.setAttempt(at);
        e.setEventType(req.eventType());
        e.setOccurredAt(now);
        e.setClientOccurredAt(req.clientTimestamp());
        e.setMetadata(metadata);
        e.setWarningCount(at.getProctoringWarningCount());
        eventRepository.save(e);
        attemptRepository.saveAndFlush(at);
        if (autoSubmit) {
            // Institution policy: the attempt ends and its saved answers are graded; nothing is deducted.
            // The attempt row is already locked above, as finalizeAttempt requires.
            attemptService.finalizeAttempt(at, AttemptStatus.AUTO_SUBMITTED, now, "PROCTORING");
        }

        Student s = at.getStudent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", e.getId());
        payload.put("attemptId", at.getId());
        payload.put("studentId", s.getId());
        payload.put("registerNumber", s.getRegisterNumber());
        payload.put("fullName", s.getFullName());
        payload.put("eventType", e.getEventType());
        payload.put("occurredAt", now);
        payload.put("warningCount", at.getProctoringWarningCount());
        payload.put("flagged", at.isProctoringFlagged());
        payload.put("autoSubmitted", autoSubmit);
        hub.toAdmins(a.getId(), "proctoring_event_recorded", payload);
        return response(a, at, warning, autoSubmit ? "PROCTORING" : null, req.eventType());
    }

    private boolean mergedWithRecentLeave(Attempt at, ProctoringEventType type, Instant now) {
        if (!type.isWarning()) return false;
        ProctoringEventType other = type == ProctoringEventType.FOCUS_LOST
                ? ProctoringEventType.FULLSCREEN_EXIT : ProctoringEventType.FOCUS_LOST;
        Instant last = eventRepository.lastOccurredAt(at.getId(), other);
        return last != null && !last.isBefore(now.minusSeconds(LEAVE_MERGE_SECONDS));
    }

    /** @param autoSubmitReason "PROCTORING" or "FULLSCREEN" when this request ended the attempt, else null */
    private ProctoringEventResponse response(Assignment a, Attempt at, boolean warning, String autoSubmitReason) {
        return response(a, at, warning, autoSubmitReason, null);
    }

    private ProctoringEventResponse response(Assignment a, Attempt at, boolean warning, String autoSubmitReason,
                                             ProctoringEventType type) {
        int count = at.getProctoringWarningCount();
        int threshold = a.getProctoringWarningThreshold();
        boolean autoSubmitted = autoSubmitReason != null;
        // The auto-submit notice is always shown, even when routine warnings are hidden.
        boolean show = autoSubmitted || (warning && a.isProctoringShowWarnings());
        String message;
        if ("FULLSCREEN".equals(autoSubmitReason)) {
            message = "You did not return to fullscreen within " + FULLSCREEN_RETURN_SECONDS
                    + " seconds, so your quiz was submitted automatically. Your saved answers have been graded.";
        } else if (autoSubmitted) {
            message = "You left the quiz page " + AUTO_SUBMIT_WARNINGS + " times, so your quiz was submitted automatically. "
                    + "Your saved answers have been graded.";
        } else if (show && type == ProctoringEventType.FULLSCREEN_EXIT && a.isProctoringRequireFullscreen()) {
            message = "Leaving fullscreen was recorded (" + count + " of " + AUTO_SUBMIT_WARNINGS + "). Return within "
                    + FULLSCREEN_RETURN_SECONDS + " seconds; at " + AUTO_SUBMIT_WARNINGS + " your quiz is submitted automatically.";
        } else if (show) {
            message = "Leaving the quiz page was recorded (" + count + " of " + AUTO_SUBMIT_WARNINGS + "). "
                    + "At " + AUTO_SUBMIT_WARNINGS + " your quiz is submitted automatically.";
        } else {
            message = null;
        }
        Instant deadline = at.getStatus() == AttemptStatus.IN_PROGRESS ? AttemptService.fullscreenDeadline(at) : null;
        return new ProctoringEventResponse(count, threshold, show, at.isProctoringFlagged(), autoSubmitted,
                autoSubmitReason, deadline, message);
    }

    /** Keeps only a few small, non-identifying fields. */
    static Map<String, Object> sanitize(Map<String, Object> in) {
        if (in == null || in.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        Object away = in.get("awayMs");
        if (away instanceof Number n && n.longValue() >= 0) out.put("awayMs", Math.min(n.longValue(), 86_400_000L));
        Object vis = in.get("visibilityState");
        if (vis instanceof String v) out.put("visibilityState", Text.truncate(v, 20));
        Object fs = in.get("fullscreenSupported");
        if (fs instanceof Boolean b) out.put("fullscreenSupported", b);
        if ("page_load".equals(in.get("reason"))) out.put("reason", "page_load");
        return out.isEmpty() ? null : out;
    }

    // ------------------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public AssignmentEvents forAssignment(Long assignmentId, AuthUser user) {
        Assignment a = assignmentService.findOwned(assignmentId, user);
        Map<Attempt, List<ProctoringEvent>> byAttempt = eventRepository.findByAssignmentId(assignmentId).stream()
                .collect(Collectors.groupingBy(ProctoringEvent::getAttempt, LinkedHashMap::new, Collectors.toList()));
        List<AttemptEvents> attempts = byAttempt.entrySet().stream().map(en -> {
            Attempt at = en.getKey();
            List<ProctoringEvent> events = en.getValue();
            int pending = (int) events.stream()
                    .filter(e -> e.getEventType().isWarning() && e.getReviewStatus() == ReviewStatus.PENDING).count();
            return new AttemptEvents(at.getId(), at.getStudent().getId(), at.getStudent().getRegisterNumber(),
                    at.getStudent().getFullName(), at.getStatus(), at.getProctoringWarningCount(),
                    at.isProctoringFlagged(), pending, events.stream().map(EventView::of).toList());
        }).sorted(Comparator.comparing(AttemptEvents::flagged).reversed()
                .thenComparing(AttemptEvents::warningCount, Comparator.reverseOrder())
                .thenComparing(AttemptEvents::registerNumber)).toList();
        return new AssignmentEvents(a.getId(), a.getName(), a.getProctoringWarningThreshold(), attempts);
    }

    @Transactional
    public EventView review(Long eventId, ReviewRequest req, AuthUser user) {
        ProctoringEvent e = eventRepository.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        if (!e.getAttempt().getAssignment().getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Event");
        }
        applyReview(e, req, user);
        return EventView.of(e);
    }

    /** Marks every event of one attempt with the same review decision. */
    @Transactional
    public int reviewAttempt(Long attemptId, ReviewRequest req, AuthUser user) {
        Attempt at = attemptRepository.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        if (!at.getAssignment().getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Attempt");
        }
        List<ProctoringEvent> events = eventRepository.findByAttemptId(attemptId);
        events.forEach(e -> applyReview(e, req, user));
        return events.size();
    }

    private void applyReview(ProctoringEvent e, ReviewRequest req, AuthUser user) {
        e.setReviewStatus(req.reviewStatus());
        e.setReviewNotes(Text.trimToNull(req.notes()));
        if (req.reviewStatus() == ReviewStatus.PENDING) {
            e.setReviewedBy(null);
            e.setReviewedAt(null);
        } else {
            e.setReviewedBy(userRepository.getReferenceById(user.userId()));
            e.setReviewedAt(Instant.now());
        }
    }
}
