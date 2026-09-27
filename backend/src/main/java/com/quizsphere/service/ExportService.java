package com.quizsphere.service;

import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.*;
import com.quizsphere.security.AuthUser;
import com.quizsphere.util.Text;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds .xlsx exports from PostgreSQL (the source of truth). Every text cell is guarded
 * against spreadsheet formula injection; numbers and timestamps are written as typed cells.
 */
@Service
public class ExportService {

    public enum NonSubmitterMarks { BLANK, ZERO }

    public record ExportFile(String filename, byte[] content) {
    }

    private static final List<String> MARKSHEET_HEADERS = List.of("Register Number", "Student Name", "Year",
            "Department", "Section", "Group/Course", "Quiz", "Assignment", "Maximum Marks", "Marks Obtained",
            "Correct Answers", "Total Questions", "Status", "Started At", "Submitted At");
    private static final List<String> RESPONSE_HEADERS = List.of("Register Number", "Student Name", "Quiz",
            "Assignment", "Question No.", "Question", "Selected Answer", "Correct Answer", "Marks Awarded",
            "Maximum Marks", "Answered At", "Status");

    private final AssignmentService assignmentService;
    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final StudentGroupRepository groupRepository;
    private final StudentRepository studentRepository;
    private final EligibilityService eligibility;
    private final GroupService groupService;

    public ExportService(AssignmentService assignmentService, AssignmentRepository assignmentRepository,
                         AttemptRepository attemptRepository, AnswerRepository answerRepository,
                         QuestionRepository questionRepository, QuizRepository quizRepository,
                         StudentGroupRepository groupRepository, StudentRepository studentRepository,
                         EligibilityService eligibility, GroupService groupService) {
        this.assignmentService = assignmentService;
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.eligibility = eligibility;
        this.groupService = groupService;
    }

    /** One assigned student with the attempt that counts for them (best final attempt, else running). */
    private record Entry(Assignment assignment, Student student, Attempt attempt, String groupLabel) {
    }

    // ------------------------------------------------------------------ public API

    @Transactional(readOnly = true)
    public ExportFile assignmentMarksheet(Long assignmentId, Long groupId, NonSubmitterMarks mode, boolean withResponses,
                                          ZoneId zone, AuthUser user) {
        Assignment a = assignmentService.findOwned(assignmentId, user);
        StudentGroup group = groupId == null ? null : loadGroup(groupId);
        List<Entry> entries = entries(a, group);
        String label = a.getQuiz().getTitle() + " - " + a.getName() + (group == null ? "" : " - " + group.getName());
        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            marksheetSheet(wb, st, entries, mode, zone);
            if (withResponses) responsesSheet(wb, st, entries, zone);
            summarySheet(wb, st, List.of(a), entries, label, user, zone, group);
            return new ExportFile(filename("Marksheet", label, zone), bytes(wb));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public ExportFile assignmentResponses(Long assignmentId, ZoneId zone, AuthUser user) {
        Assignment a = assignmentService.findOwned(assignmentId, user);
        List<Entry> entries = entries(a, null);
        String label = a.getQuiz().getTitle() + " - " + a.getName();
        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            responsesSheet(wb, st, entries, zone);
            summarySheet(wb, st, List.of(a), entries, label, user, zone, null);
            return new ExportFile(filename("Responses", label, zone), bytes(wb));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public ExportFile quizMarksheet(Long quizId, NonSubmitterMarks mode, ZoneId zone, AuthUser user) {
        Quiz quiz = quizRepository.findById(quizId).filter(q -> q.getOwner().getId().equals(user.userId()))
                .orElseThrow(() -> ApiException.notFound("Quiz"));
        List<Assignment> assignments = assignmentRepository.findByQuizIdOrderByCreatedAtAsc(quizId);
        List<Entry> entries = new ArrayList<>();
        assignments.forEach(a -> entries.addAll(entries(a, null)));
        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            marksheetSheet(wb, st, entries, mode, zone);
            responsesSheet(wb, st, entries, zone);
            summarySheet(wb, st, assignments, entries, quiz.getTitle(), user, zone, null);
            return new ExportFile(filename("Marksheet", quiz.getTitle(), zone), bytes(wb));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One row per student across several assignments, optionally limited to one group. */
    @Transactional(readOnly = true)
    public ExportFile consolidated(List<Long> assignmentIds, Long groupId, NonSubmitterMarks mode, ZoneId zone,
                                   AuthUser user) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            throw ApiException.badRequest("Select at least one assignment");
        }
        List<Assignment> assignments = new LinkedHashSet<>(assignmentIds).stream()
                .map(id -> assignmentService.findOwned(id, user)).toList();
        StudentGroup group = groupId == null ? null : loadGroup(groupId);
        Map<Long, Map<Long, Entry>> byAssignment = new LinkedHashMap<>();
        TreeMap<String, Student> students = new TreeMap<>();
        for (Assignment a : assignments) {
            Map<Long, Entry> m = new HashMap<>();
            for (Entry e : entries(a, group)) {
                m.put(e.student().getId(), e);
                students.put(e.student().getRegisterNumber(), e.student());
            }
            byAssignment.put(a.getId(), m);
        }
        String label = group == null ? "Consolidated" : "Consolidated - " + group.getName();
        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet("Marksheet");
            List<String> headers = new ArrayList<>(List.of("Register Number", "Student Name", "Year", "Department", "Section"));
            for (Assignment a : assignments) {
                headers.add(a.getQuiz().getTitle() + " / " + a.getName() + " (max " + fmt(maxMarks(a)) + ")");
            }
            headers.addAll(List.of("Total Marks", "Maximum Total", "Percentage"));
            header(sh, st, headers);
            int r = 1;
            BigDecimal maxTotal = assignments.stream().map(this::maxMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Student s : students.values()) {
                Row row = sh.createRow(r++);
                int c = 0;
                text(row, c++, s.getRegisterNumber(), st);
                text(row, c++, s.getFullName(), st);
                text(row, c++, Text.roman(s.getAcademicYear()), st);
                text(row, c++, s.getDepartment(), st);
                text(row, c++, s.getSection(), st);
                BigDecimal total = BigDecimal.ZERO;
                for (Assignment a : assignments) {
                    Entry e = byAssignment.get(a.getId()).get(s.getId());
                    BigDecimal score = finalScore(e);
                    if (score != null) {
                        number(row, c, score, st.marks);
                        total = total.add(score);
                    } else if (e != null && mode == NonSubmitterMarks.ZERO) {
                        number(row, c, BigDecimal.ZERO, st.marks);
                    } else {
                        text(row, c, e == null ? "NOT_ASSIGNED" : "NOT_SUBMITTED", st);
                    }
                    c++;
                }
                number(row, c++, total, st.marks);
                number(row, c++, maxTotal, st.marks);
                number(row, c, maxTotal.signum() == 0 ? BigDecimal.ZERO
                        : total.multiply(BigDecimal.valueOf(100)).divide(maxTotal, 2, RoundingMode.HALF_UP), st.marks);
            }
            finish(sh, headers.size(), r);
            List<Entry> all = byAssignment.values().stream().flatMap(m -> m.values().stream()).toList();
            summarySheet(wb, st, assignments, all, label, user, zone, group);
            return new ExportFile(filename("Consolidated", group == null ? "Marks" : group.getName(), zone), bytes(wb));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- data gathering

    private List<Entry> entries(Assignment a, StudentGroup filterGroup) {
        Map<Long, List<Attempt>> byStudent = attemptRepository.findByAssignmentIdOrderByIdAsc(a.getId()).stream()
                .collect(Collectors.groupingBy(x -> x.getStudent().getId()));
        List<Student> assigned = eligibility.assignedStudents(a);
        List<Student> all = null;
        boolean needAll = (filterGroup != null && filterGroup.getMembershipMode() == MembershipMode.DYNAMIC)
                || a.getGroups().stream().anyMatch(g -> g.getMembershipMode() == MembershipMode.DYNAMIC);
        if (needAll) all = studentRepository.findAllWithCourses();
        Set<Long> filterIds = filterGroup == null ? null : groupService.members(filterGroup, all == null ? List.of() : all)
                .stream().map(Student::getId).collect(Collectors.toSet());

        // Label each student with the assignment groups they belong to, or the quiz's course.
        Map<Long, List<String>> labels = new HashMap<>();
        for (StudentGroup g : a.getGroups()) {
            StudentGroup loaded = g.getMembershipMode() == MembershipMode.MANUAL
                    ? groupRepository.findWithMembersById(g.getId()).orElse(g) : g;
            for (Student s : groupService.members(loaded, all == null ? List.of() : all)) {
                labels.computeIfAbsent(s.getId(), k -> new ArrayList<>()).add(g.getName());
            }
        }
        String course = a.getQuiz().getCourse() == null ? "" : a.getQuiz().getCourse().getCourseCode();
        List<Entry> out = new ArrayList<>();
        for (Student s : assigned) {
            if (filterIds != null && !filterIds.contains(s.getId())) continue;
            Attempt counted = AssignmentRules.counted(byStudent.getOrDefault(s.getId(), List.of())).orElse(null);
            String label = labels.containsKey(s.getId()) ? String.join(", ", labels.get(s.getId())) : course;
            out.add(new Entry(a, s, counted, label));
        }
        return out;
    }

    private StudentGroup loadGroup(Long id) {
        return groupRepository.findWithMembersById(id).orElseThrow(() -> ApiException.notFound("Group"));
    }

    private BigDecimal maxMarks(Assignment a) {
        return questionRepository.findByQuizIdWithOptions(a.getQuiz().getId()).stream()
                .map(Question::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal finalScore(Entry e) {
        return e != null && e.attempt() != null && e.attempt().getStatus().isFinal() ? e.attempt().getScore() : null;
    }

    private static String status(Entry e) {
        return e.attempt() == null ? "NOT_SUBMITTED" : e.attempt().getStatus().name();
    }

    // ----------------------------------------------------------------------- sheets

    private void marksheetSheet(Workbook wb, Styles st, List<Entry> entries, NonSubmitterMarks mode, ZoneId zone) {
        Sheet sh = wb.createSheet("Marksheet");
        List<String> headers = new ArrayList<>(MARKSHEET_HEADERS);
        headers.set(13, "Started At (" + zone.getId() + ")");
        headers.set(14, "Submitted At (" + zone.getId() + ")");
        header(sh, st, headers);
        Map<Long, BigDecimal> maxByQuiz = new HashMap<>();
        int r = 1;
        for (Entry e : entries) {
            Student s = e.student();
            Attempt at = e.attempt();
            BigDecimal max = maxByQuiz.computeIfAbsent(e.assignment().getQuiz().getId(), k -> maxMarks(e.assignment()));
            Row row = sh.createRow(r++);
            text(row, 0, s.getRegisterNumber(), st);
            text(row, 1, s.getFullName(), st);
            text(row, 2, Text.roman(s.getAcademicYear()), st);
            text(row, 3, s.getDepartment(), st);
            text(row, 4, s.getSection(), st);
            text(row, 5, e.groupLabel(), st);
            text(row, 6, e.assignment().getQuiz().getTitle(), st);
            text(row, 7, e.assignment().getName(), st);
            number(row, 8, max, st.marks);
            BigDecimal score = finalScore(e);
            if (score != null) number(row, 9, score, st.marks);
            else if (mode == NonSubmitterMarks.ZERO) number(row, 9, BigDecimal.ZERO, st.marks);
            if (at != null && at.getStatus().isFinal() && at.getCorrectCount() != null) {
                number(row, 10, BigDecimal.valueOf(at.getCorrectCount()), st.integer);
            }
            number(row, 11, BigDecimal.valueOf(at != null ? at.getQuestionCount()
                    : questionRepository.findByQuizIdWithOptions(e.assignment().getQuiz().getId()).size()), st.integer);
            text(row, 12, status(e), st);
            time(row, 13, at == null ? null : at.getStartedAt(), zone, st);
            time(row, 14, at == null ? null : at.getSubmittedAt(), zone, st);
        }
        finish(sh, headers.size(), r);
    }

    private void responsesSheet(Workbook wb, Styles st, List<Entry> entries, ZoneId zone) {
        Sheet sh = wb.createSheet("Detailed Responses");
        List<String> headers = new ArrayList<>(RESPONSE_HEADERS);
        headers.set(10, "Answered At (" + zone.getId() + ")");
        header(sh, st, headers);
        Map<Long, List<Question>> questionsByQuiz = new HashMap<>();
        List<Long> attemptIds = entries.stream().filter(e -> e.attempt() != null).map(e -> e.attempt().getId()).toList();
        Map<Long, Map<Long, Answer>> answers = attemptIds.isEmpty() ? Map.of()
                : answerRepository.findByAttemptIdIn(attemptIds).stream().collect(Collectors.groupingBy(
                x -> x.getAttempt().getId(), Collectors.toMap(Answer::getQuestionId, x -> x)));
        int r = 1;
        for (Entry e : entries) {
            if (e.attempt() == null) continue;
            List<Question> questions = questionsByQuiz.computeIfAbsent(e.assignment().getQuiz().getId(),
                    questionRepository::findByQuizIdWithOptions);
            Map<Long, Answer> mine = answers.getOrDefault(e.attempt().getId(), Map.of());
            boolean isFinal = e.attempt().getStatus().isFinal();
            int n = 1;
            for (Question q : questions) {
                Answer ans = mine.get(q.getId());
                Row row = sh.createRow(r++);
                text(row, 0, e.student().getRegisterNumber(), st);
                text(row, 1, e.student().getFullName(), st);
                text(row, 2, e.assignment().getQuiz().getTitle(), st);
                text(row, 3, e.assignment().getName(), st);
                number(row, 4, BigDecimal.valueOf(n++), st.integer);
                text(row, 5, q.getText(), st);
                text(row, 6, optionLabel(q, ans == null ? null : ans.getOptionId()), st);
                text(row, 7, optionLabel(q, q.correctOption().map(QuestionOption::getId).orElse(null)), st);
                if (isFinal) {
                    number(row, 8, ans == null || ans.getMarksAwarded() == null ? BigDecimal.ZERO : ans.getMarksAwarded(), st.marks);
                }
                number(row, 9, q.getPoints(), st.marks);
                time(row, 10, ans == null ? null : ans.getUpdatedAt(), zone, st);
                String status = ans == null || ans.getOptionId() == null ? "UNANSWERED"
                        : !isFinal ? "IN_PROGRESS" : Boolean.TRUE.equals(ans.getCorrect()) ? "CORRECT" : "INCORRECT";
                text(row, 11, status, st);
            }
        }
        finish(sh, headers.size(), r);
    }

    private void summarySheet(Workbook wb, Styles st, List<Assignment> assignments, List<Entry> entries, String title,
                              AuthUser user, ZoneId zone, StudentGroup group) {
        Sheet sh = wb.createSheet("Summary");
        List<BigDecimal> scores = entries.stream().map(ExportService::finalScore).filter(Objects::nonNull).toList();
        Map<String, Long> byStatus = entries.stream().collect(Collectors.groupingBy(ExportService::status, Collectors.counting()));
        Object[][] rows = {
                {"Export", title},
                {"Quiz(zes)", assignments.stream().map(a -> a.getQuiz().getTitle()).distinct().collect(Collectors.joining(", "))},
                {"Assignment(s)", assignments.stream().map(Assignment::getName).collect(Collectors.joining(", "))},
                {"Group filter", group == null ? "All assigned students" : group.getName()},
                {"Exported at (" + zone.getId() + ")", ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))},
                {"Exported by", user.name()},
                {"Assigned students (rows)", (long) entries.size()},
                {"Submitted", byStatus.getOrDefault("SUBMITTED", 0L)},
                {"Auto-submitted", byStatus.getOrDefault("AUTO_SUBMITTED", 0L)},
                {"In progress", byStatus.getOrDefault("IN_PROGRESS", 0L)},
                {"Not submitted", byStatus.getOrDefault("NOT_SUBMITTED", 0L)},
                {"Average marks (submitted)", scores.isEmpty() ? null : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP)},
                {"Highest marks", scores.stream().max(Comparator.naturalOrder()).orElse(null)},
                {"Lowest marks", scores.stream().min(Comparator.naturalOrder()).orElse(null)},
                {"Note", "Generated from the QuizSphere database. Proctoring signals are not included and are not proof of misconduct."},
        };
        int r = 0;
        for (Object[] kv : rows) {
            Row row = sh.createRow(r++);
            Cell k = row.createCell(0);
            k.setCellValue((String) kv[0]);
            k.setCellStyle(st.bold);
            Object v = kv[1];
            if (v instanceof Number n) number(row, 1, new BigDecimal(n.toString()), st.marks);
            else if (v != null) text(row, 1, v.toString(), st);
        }
        sh.setColumnWidth(0, 32 * 256);
        sh.setColumnWidth(1, 70 * 256);
    }

    private static String optionLabel(Question q, Long optionId) {
        if (optionId == null) return "";
        List<QuestionOption> opts = q.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).getId().equals(optionId)) return (char) ('A' + i) + ". " + opts.get(i).getText();
        }
        return "";
    }

    // ---------------------------------------------------------------------- styling

    private static final class Styles {
        final CellStyle header, bold, text, marks, integer, time;

        Styles(Workbook wb) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            header = wb.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);
            bold = wb.createCellStyle();
            bold.setFont(boldFont);
            text = wb.createCellStyle();
            DataFormat df = wb.createDataFormat();
            marks = wb.createCellStyle();
            marks.setDataFormat(df.getFormat("0.##"));
            integer = wb.createCellStyle();
            integer.setDataFormat(df.getFormat("0"));
            time = wb.createCellStyle();
            time.setDataFormat(df.getFormat("yyyy-mm-dd hh:mm:ss"));
        }
    }

    private static void header(Sheet sh, Styles st, List<String> headers) {
        Row row = sh.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(st.header);
        }
    }

    private static void finish(Sheet sh, int columns, int rowCount) {
        sh.createFreezePane(0, 1);
        sh.setAutoFilter(new CellRangeAddress(0, Math.max(rowCount - 1, 0), 0, columns - 1));
        for (int c = 0; c < columns; c++) {
            int max = 10;
            for (int r = 0; r < Math.min(rowCount, 500); r++) {
                Row row = sh.getRow(r);
                Cell cell = row == null ? null : row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    max = Math.max(max, Math.min(cell.getStringCellValue().length() + 2, 60));
                }
            }
            sh.setColumnWidth(c, max * 256);
        }
    }

    /** Writes text, neutralizing values Excel would interpret as a formula (=, +, -, @, tab, CR). */
    static String safe(String v) {
        if (v == null || v.isEmpty()) return v;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + v;
        }
        return v;
    }

    private static void text(Row row, int col, String value, Styles st) {
        if (value == null) return;
        Cell c = row.createCell(col, CellType.STRING);
        c.setCellValue(safe(value));
        c.setCellStyle(st.text);
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        if (value == null) return;
        Cell c = row.createCell(col, CellType.NUMERIC);
        c.setCellValue(value.doubleValue());
        c.setCellStyle(style);
    }

    private static void time(Row row, int col, Instant value, ZoneId zone, Styles st) {
        if (value == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(LocalDateTime.ofInstant(value, zone));
        c.setCellStyle(st.time);
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static String filename(String kind, String label, ZoneId zone) {
        String clean = label.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        if (clean.length() > 80) clean = clean.substring(0, 80);
        String stamp = ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return "QuizSphere_" + kind + "_" + (clean.isEmpty() ? "export" : clean) + "_" + stamp + ".xlsx";
    }

    public static ZoneId zone(String tz) {
        try {
            return tz == null || tz.isBlank() ? ZoneOffset.UTC : ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
