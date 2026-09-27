package com.quizsphere.service;

import com.quizsphere.entity.*;
import com.quizsphere.repository.*;
import com.quizsphere.security.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {

    public record RecentSubmission(Long attemptId, Long assignmentId, String assignmentName, String quizTitle,
                                   String registerNumber, String fullName, AttemptStatus status, BigDecimal score,
                                   BigDecimal maximumScore, Instant submittedAt) {
    }

    public record AdminDashboard(long totalStudents, long activeStudents, long courses, long groups,
                                 long draftQuizzes, long publishedQuizzes, long activeAssignments,
                                 long liveSessions, long pendingProctoringReviews,
                                 List<RecentSubmission> recentSubmissions) {
    }

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final StudentGroupRepository groupRepository;
    private final QuizRepository quizRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final ProctoringEventRepository eventRepository;

    public DashboardService(StudentRepository studentRepository, CourseRepository courseRepository,
                            StudentGroupRepository groupRepository, QuizRepository quizRepository,
                            AssignmentRepository assignmentRepository, AttemptRepository attemptRepository,
                            ProctoringEventRepository eventRepository) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.groupRepository = groupRepository;
        this.quizRepository = quizRepository;
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboard get(AuthUser user) {
        Long uid = user.userId();
        List<RecentSubmission> recent = attemptRepository.findRecentSubmissions(uid, PageRequest.of(0, 10)).stream()
                .map(a -> new RecentSubmission(a.getId(), a.getAssignment().getId(), a.getAssignment().getName(),
                        a.getAssignment().getQuiz().getTitle(), a.getStudent().getRegisterNumber(),
                        a.getStudent().getFullName(), a.getStatus(), a.getScore(), a.getMaximumScore(),
                        a.getSubmittedAt()))
                .toList();
        return new AdminDashboard(studentRepository.count(), studentRepository.countByActiveTrue(),
                courseRepository.count(), groupRepository.count(),
                quizRepository.countByOwnerIdAndStatus(uid, QuizStatus.DRAFT),
                quizRepository.countByOwnerIdAndStatus(uid, QuizStatus.PUBLISHED),
                assignmentRepository.countByOwnerIdAndStatus(uid, AssignmentStatus.ACTIVE),
                assignmentRepository.countByOwnerIdAndSessionState(uid, SessionState.LIVE),
                eventRepository.countWarningsByOwnerAndStatus(uid, ReviewStatus.PENDING), recent);
    }
}
