package com.quizsphere.service;

import com.quizsphere.dto.PageResponse;
import com.quizsphere.dto.StudentDtos.*;
import com.quizsphere.entity.Course;
import com.quizsphere.entity.Role;
import com.quizsphere.entity.Student;
import com.quizsphere.entity.User;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.CourseRepository;
import com.quizsphere.repository.StudentRepository;
import com.quizsphere.repository.UserRepository;
import com.quizsphere.util.SpreadsheetReader;
import com.quizsphere.util.Text;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentService {

    /** Roster template columns (PRD section 4), plus optional Program and Password. */
    public static final List<String> ROSTER_HEADERS = List.of("Register Number", "Student Name", "Email",
            "Academic Year", "Department", "Section", "Semester", "Course Codes", "Program", "Password");
    private static final List<String> REQUIRED_HEADERS = List.of("registernumber", "studentname", "academicyear", "department");

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository studentRepository, UserRepository userRepository,
                          CourseRepository courseRepository, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(String q, Integer year, String department, String section,
                                              Boolean active, Long courseId, int page, int size) {
        Specification<Student> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            String term = Text.trimToNull(q);
            if (term != null) {
                String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("registerNumber")), like),
                        cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (year != null) ps.add(cb.equal(root.get("academicYear"), year));
            if (Text.trimToNull(department) != null) ps.add(cb.equal(cb.upper(root.get("department")), department.trim().toUpperCase()));
            if (Text.trimToNull(section) != null) ps.add(cb.equal(cb.upper(root.get("section")), section.trim().toUpperCase()));
            if (active != null) ps.add(cb.equal(root.get("active"), active));
            if (courseId != null) {
                ps.add(cb.equal(root.join("courses", JoinType.INNER).get("id"), courseId));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by("registerNumber"));
        return PageResponse.of(studentRepository.findAll(spec, pageable), StudentResponse::of);
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return StudentResponse.of(find(id));
    }

    @Transactional(readOnly = true)
    public AttributeOptions attributes() {
        return new AttributeOptions(studentRepository.distinctDepartments(), studentRepository.distinctSections(),
                studentRepository.distinctPrograms());
    }

    @Transactional
    public StudentResponse create(CreateStudentRequest req) {
        String regNo = Text.normalizeRegisterNumber(req.registerNumber());
        if (regNo == null || !Text.REGISTER_NUMBER.matcher(regNo).matches()) {
            throw ApiException.badRequest("Register number must be 3-40 letters, digits, / - _");
        }
        if (studentRepository.existsByRegisterNumber(regNo)) {
            throw ApiException.conflict("Register number " + regNo + " already exists");
        }
        Student s = newStudent(regNo, req.fullName().trim(), Text.trimToNull(req.email()), req.academicYear(),
                req.department(), req.section(), req.program(), req.semester(), req.password());
        if (req.courseIds() != null) {
            s.getCourses().addAll(resolveCourses(req.courseIds()));
        }
        return StudentResponse.of(studentRepository.saveAndFlush(s));
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest req) {
        Student s = find(id);
        if (req.fullName() != null) {
            s.setFullName(req.fullName().trim());
            s.getUser().setName(req.fullName().trim());
        }
        if (req.email() != null) s.getUser().setEmail(Text.trimToNull(req.email()));
        if (req.academicYear() != null) s.setAcademicYear(req.academicYear());
        if (req.department() != null) s.setDepartment(Text.upperOrNull(req.department()));
        if (req.section() != null) s.setSection(Text.upperOrNull(req.section()));
        if (req.program() != null) s.setProgram(Text.upperOrNull(req.program()));
        if (req.semester() != null) s.setSemester(req.semester());
        if (req.active() != null) {
            s.setActive(req.active());
            s.getUser().setActive(req.active());
        }
        if (req.courseIds() != null) {
            s.getCourses().clear();
            s.getCourses().addAll(resolveCourses(req.courseIds()));
        }
        if (req.resetPassword() != null) {
            s.getUser().setPasswordHash(passwordEncoder.encode(req.resetPassword()));
            s.getUser().setMustChangePassword(true);
        }
        return StudentResponse.of(studentRepository.saveAndFlush(s));
    }

    private Student find(Long id) {
        return studentRepository.findWithCoursesById(id).orElseThrow(() -> ApiException.notFound("Student"));
    }

    private Set<Course> resolveCourses(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids);
        List<Course> found = courseRepository.findAllById(unique);
        if (found.size() != unique.size()) {
            throw ApiException.badRequest("One or more courses do not exist");
        }
        return new HashSet<>(found);
    }

    /**
     * Creates a student and login. Without an explicit password the initial password is the
     * register number, and the student must change it at first login.
     */
    private Student newStudent(String regNo, String name, String email, Integer year, String department,
                               String section, String program, Integer semester, String password) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setRole(Role.STUDENT);
        user.setPasswordHash(passwordEncoder.encode(password != null ? password : regNo));
        user.setMustChangePassword(true);
        userRepository.save(user);

        Student s = new Student();
        s.setUser(user);
        s.setRegisterNumber(regNo);
        s.setFullName(name);
        s.setAcademicYear(year);
        s.setDepartment(Text.upperOrNull(department));
        s.setSection(Text.upperOrNull(section));
        s.setProgram(Text.upperOrNull(program));
        s.setSemester(semester);
        return s;
    }

    // ---------------------------------------------------------------- roster import

    private record ParsedRow(SpreadsheetReader.Row raw, String regNo, String name, String email, Integer year,
                             String department, String section, String program, Integer semester,
                             List<Course> courses, String password, List<String> errors, List<String> warnings) {
    }

    /**
     * Previews or commits a roster import. Existing register numbers are never overwritten unless
     * {@code updateExisting} is explicitly set. Invalid rows block the commit unless
     * {@code skipInvalid} is explicitly set.
     */
    @Transactional
    public RosterImportResult importRoster(MultipartFile file, boolean commit, boolean updateExisting,
                                           boolean skipInvalid) {
        SpreadsheetReader.Sheet sheet = SpreadsheetReader.read(file);
        List<String> headerErrors = new ArrayList<>();
        for (String h : REQUIRED_HEADERS) {
            if (!sheet.headers().contains(h)) {
                headerErrors.add("Missing required column: " + ROSTER_HEADERS.stream()
                        .filter(x -> SpreadsheetReader.normalizeHeader(x).equals(h)).findFirst().orElse(h));
            }
        }
        if (!headerErrors.isEmpty()) {
            return new RosterImportResult(sheet.fileName(), false, sheet.rows().size(), 0, 0, 0, 0, headerErrors, List.of());
        }

        Map<String, Course> coursesByCode = courseRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getCourseCode().toUpperCase(Locale.ROOT), c -> c));
        Set<String> regNos = sheet.rows().stream().map(r -> Text.normalizeRegisterNumber(r.get("registernumber")))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Student> existing = studentRepository.findByRegisterNumberIn(regNos).stream()
                .collect(Collectors.toMap(Student::getRegisterNumber, s -> s));

        Set<String> seen = new HashSet<>();
        List<ParsedRow> parsed = new ArrayList<>();
        for (SpreadsheetReader.Row r : sheet.rows()) {
            parsed.add(parseRow(r, coursesByCode, seen));
        }

        List<RosterImportRow> rows = new ArrayList<>();
        int create = 0, update = 0, skip = 0, invalid = 0;
        for (ParsedRow p : parsed) {
            RowAction action;
            if (!p.errors().isEmpty()) {
                action = RowAction.INVALID;
                invalid++;
            } else if (existing.containsKey(p.regNo())) {
                if (updateExisting) {
                    action = RowAction.UPDATE;
                    update++;
                } else {
                    action = RowAction.SKIP_EXISTING;
                    p.warnings().add("Register number already exists; row will be skipped (enable 'update existing' to update it)");
                    skip++;
                }
            } else {
                action = RowAction.CREATE;
                create++;
            }
            rows.add(new RosterImportRow(p.raw().rowNumber(), p.regNo(), p.name(), action, p.errors(), p.warnings(),
                    p.raw().values()));
        }

        boolean canCommit = invalid == 0 || skipInvalid;
        if (commit && !canCommit) {
            throw ApiException.unprocessable(invalid + " row(s) are invalid. Fix them or explicitly choose to skip invalid rows.",
                    new RosterImportResult(sheet.fileName(), false, parsed.size(), create, update, skip, invalid, headerErrors, rows));
        }
        if (commit) {
            for (int i = 0; i < parsed.size(); i++) {
                ParsedRow p = parsed.get(i);
                RowAction action = rows.get(i).action();
                if (action == RowAction.CREATE) {
                    Student s = newStudent(p.regNo(), p.name(), p.email(), p.year(), p.department(), p.section(),
                            p.program(), p.semester(), p.password());
                    s.getCourses().addAll(p.courses());
                    studentRepository.save(s);
                } else if (action == RowAction.UPDATE) {
                    Student s = existing.get(p.regNo());
                    s.setFullName(p.name());
                    s.getUser().setName(p.name());
                    if (p.email() != null) s.getUser().setEmail(p.email());
                    s.setAcademicYear(p.year());
                    s.setDepartment(Text.upperOrNull(p.department()));
                    if (p.section() != null) s.setSection(Text.upperOrNull(p.section()));
                    if (p.program() != null) s.setProgram(Text.upperOrNull(p.program()));
                    if (p.semester() != null) s.setSemester(p.semester());
                    s.getCourses().addAll(p.courses()); // enrollment is additive; never silently removed
                }
            }
            studentRepository.flush();
        }
        return new RosterImportResult(sheet.fileName(), commit, parsed.size(), create, update, skip, invalid,
                headerErrors, rows);
    }

    private ParsedRow parseRow(SpreadsheetReader.Row r, Map<String, Course> coursesByCode, Set<String> seen) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String regNo = Text.normalizeRegisterNumber(r.get("registernumber"));
        if (regNo == null) {
            errors.add("Register Number is required");
        } else if (!Text.REGISTER_NUMBER.matcher(regNo).matches()) {
            errors.add("Register Number must be 3-40 letters, digits, / - _");
        } else if (!seen.add(regNo)) {
            errors.add("Duplicate register number within the file");
        }
        String name = r.get("studentname");
        if (name == null) errors.add("Student Name is required");
        else if (name.length() > 150) errors.add("Student Name is too long (max 150)");

        String email = r.get("email");
        if (email != null && !Text.EMAIL.matcher(email).matches()) errors.add("Email is not valid");

        String yearRaw = r.get("academicyear");
        Integer year = Text.parseAcademicYear(yearRaw);
        if (yearRaw == null) errors.add("Academic Year is required");
        else if (year == null) errors.add("Academic Year must be 1-6 or I-VI");

        String department = r.get("department");
        if (department == null) errors.add("Department is required");
        else if (department.length() > 50) errors.add("Department is too long (max 50)");

        String section = r.get("section");
        if (section != null && section.length() > 20) errors.add("Section is too long (max 20)");
        String program = r.get("program");
        if (program != null && program.length() > 50) errors.add("Program is too long (max 50)");

        String semRaw = r.get("semester");
        Integer semester = Text.parseInt(semRaw);
        if (semRaw != null && (semester == null || semester < 1 || semester > 12)) {
            errors.add("Semester must be a whole number 1-12");
            semester = null;
        }

        List<Course> courses = new ArrayList<>();
        String codes = r.get("coursecodes");
        if (codes != null) {
            for (String code : codes.split("[,;|]")) {
                String c = code.trim().toUpperCase(Locale.ROOT);
                if (c.isEmpty()) continue;
                Course course = coursesByCode.get(c);
                if (course == null) errors.add("Unknown course code: " + c + " (create the course first)");
                else if (!courses.contains(course)) courses.add(course);
            }
        }

        String password = r.get("password");
        if (password != null && (password.length() < 8 || password.length() > 72)) {
            errors.add("Password must be 8-72 characters");
        }
        if (password == null) {
            warnings.add("No password given: initial password will be the register number (must be changed at first login)");
        }
        return new ParsedRow(r, regNo, name, email, year, department, section, program, semester, courses, password,
                errors, warnings);
    }
}
