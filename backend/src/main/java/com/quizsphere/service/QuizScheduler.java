package com.quizsphere.service;

import com.quizsphere.config.AppProperties;
import com.quizsphere.entity.SessionState;
import com.quizsphere.repository.AssignmentRepository;
import com.quizsphere.repository.AttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Enforces deadlines even when no client is connected: expired attempts are auto-submitted
 * with their saved answers and overdue live sessions are ended. Each item is handled in its
 * own transaction with row locks, so this is safe to run alongside student requests.
 */
@Component
public class QuizScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduler.class);

    private final AttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptService attemptService;
    private final AssignmentService assignmentService;
    private final Duration grace;

    public QuizScheduler(AttemptRepository attemptRepository, AssignmentRepository assignmentRepository,
                         AttemptService attemptService, AssignmentService assignmentService, AppProperties props) {
        this.attemptRepository = attemptRepository;
        this.assignmentRepository = assignmentRepository;
        this.attemptService = attemptService;
        this.assignmentService = assignmentService;
        this.grace = Duration.ofSeconds(props.quiz().graceSeconds());
    }

    @Scheduled(fixedDelayString = "${app.quiz.auto-submit-interval-ms:10000}", initialDelay = 5000)
    public void run() {
        Instant cutoff = Instant.now().minus(grace);
        for (Long id : assignmentRepository.findLiveSessionsEndedBefore(SessionState.LIVE, cutoff)) {
            try {
                assignmentService.endSessionInternal(id);
                log.info("Live session for assignment {} ended automatically", id);
            } catch (RuntimeException e) {
                log.warn("Could not end session {}: {}", id, e.getMessage());
            }
        }
        Instant fullscreenCutoff = cutoff.minusSeconds(ProctoringService.FULLSCREEN_RETURN_SECONDS);
        for (Long id : attemptRepository.findFullscreenCountdownExpired(fullscreenCutoff)) {
            try {
                if (attemptService.autoFinalizeFullscreen(id)) {
                    log.info("Attempt {} auto-submitted: student did not return to fullscreen", id);
                }
            } catch (RuntimeException e) {
                log.warn("Could not auto-submit attempt {} (fullscreen): {}", id, e.getMessage());
            }
        }
        for (Long id : attemptRepository.findExpiredInProgress(cutoff)) {
            try {
                if (attemptService.autoFinalize(id)) {
                    log.info("Attempt {} auto-submitted after its deadline", id);
                }
            } catch (RuntimeException e) {
                log.warn("Could not auto-submit attempt {}: {}", id, e.getMessage());
            }
        }
    }
}
