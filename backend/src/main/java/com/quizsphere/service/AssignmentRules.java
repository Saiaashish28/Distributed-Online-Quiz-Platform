package com.quizsphere.service;

import com.quizsphere.entity.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure timing / visibility rules for assignments. The server clock is authoritative. */
public final class AssignmentRules {

    /** Best attempt first: highest score, then earliest submission, then lowest id (stable). */
    public static final Comparator<Attempt> BEST_ATTEMPT = Comparator
            .comparing((Attempt a) -> a.getScore() == null ? java.math.BigDecimal.valueOf(-1) : a.getScore(),
                    Comparator.reverseOrder())
            .thenComparing(a -> a.getSubmittedAt() == null ? Instant.MAX : a.getSubmittedAt())
            .thenComparing(Attempt::getId);

    private AssignmentRules() {
    }

    public static boolean resultsVisible(Assignment a, Instant now) {
        if (a.isResultsReleased()) return true;
        return switch (a.getResultsReleaseMode()) {
            case IMMEDIATE -> true;
            case AFTER_DEADLINE -> (a.getDeadline() != null && !now.isBefore(a.getDeadline()))
                    || (a.isLiveSession() && a.getSessionState() == SessionState.ENDED);
            case MANUAL -> false;
        };
    }

    /** Why a new attempt cannot be started right now (timing/session only), or null when it can. */
    public static String startBlockReason(Assignment a, Instant now) {
        if (a.getStatus() == AssignmentStatus.CLOSED) return "This quiz has been closed";
        if (a.getAvailableFrom() != null && now.isBefore(a.getAvailableFrom())) return "This quiz is not open yet";
        if (a.getDeadline() != null && !now.isBefore(a.getDeadline())) return "The deadline has passed";
        if (a.isLiveSession()) {
            if (a.getSessionState() == SessionState.WAITING || a.getSessionState() == null) {
                return "Waiting for the instructor to start the session";
            }
            if (a.getSessionState() == SessionState.ENDED) return "The session has ended";
            if (a.getSessionEndsAt() != null && !now.isBefore(a.getSessionEndsAt())) return "The session has ended";
        }
        return null;
    }

    /** True when the assignment has not started yet (as opposed to being over). */
    public static boolean isUpcoming(Assignment a, Instant now) {
        if (a.getStatus() == AssignmentStatus.CLOSED) return false;
        if (a.getAvailableFrom() != null && now.isBefore(a.getAvailableFrom())) return true;
        return a.isLiveSession() && (a.getSessionState() == null || a.getSessionState() == SessionState.WAITING)
                && (a.getDeadline() == null || now.isBefore(a.getDeadline()));
    }

    /** Official end of a new attempt: the earliest of duration, deadline and live-session end. */
    public static Instant attemptEndsAt(Assignment a, Instant now) {
        Instant end = now.plus(Duration.ofMinutes(a.getDurationMinutes()));
        if (a.getDeadline() != null && a.getDeadline().isBefore(end)) end = a.getDeadline();
        if (a.isLiveSession() && a.getSessionEndsAt() != null && a.getSessionEndsAt().isBefore(end)) {
            end = a.getSessionEndsAt();
        }
        return end;
    }

    public static Optional<Attempt> bestFinal(List<Attempt> attempts) {
        return attempts.stream().filter(x -> x.getStatus().isFinal()).min(BEST_ATTEMPT);
    }

    /** The attempt that represents the student: best finalized attempt, else the in-progress one. */
    public static Optional<Attempt> counted(List<Attempt> attempts) {
        return bestFinal(attempts).or(() -> attempts.stream()
                .filter(x -> x.getStatus() == AttemptStatus.IN_PROGRESS).findFirst());
    }
}
