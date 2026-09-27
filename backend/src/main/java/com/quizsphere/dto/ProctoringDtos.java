package com.quizsphere.dto;

import com.quizsphere.entity.AttemptStatus;
import com.quizsphere.entity.ProctoringEvent;
import com.quizsphere.entity.ProctoringEventType;
import com.quizsphere.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ProctoringDtos {

    private ProctoringDtos() {
    }

    public record EventView(Long id, ProctoringEventType eventType, Instant occurredAt, Instant clientOccurredAt,
                            Map<String, Object> metadata, int warningCount, ReviewStatus reviewStatus,
                            String reviewedBy, Instant reviewedAt, String reviewNotes) {
        public static EventView of(ProctoringEvent e) {
            return new EventView(e.getId(), e.getEventType(), e.getOccurredAt(), e.getClientOccurredAt(),
                    e.getMetadata(), e.getWarningCount(), e.getReviewStatus(),
                    e.getReviewedBy() == null ? null : e.getReviewedBy().getName(),
                    e.getReviewedAt(), e.getReviewNotes());
        }
    }

    public record AttemptEvents(Long attemptId, Long studentId, String registerNumber, String fullName,
                                AttemptStatus attemptStatus, int warningCount, boolean flagged,
                                int pendingReviewCount, List<EventView> events) {
    }

    public record AssignmentEvents(Long assignmentId, String assignmentName, int warningThreshold,
                                   List<AttemptEvents> attempts) {
    }

    public record ReviewRequest(@NotNull ReviewStatus reviewStatus, @Size(max = 1000) String notes) {
    }
}
