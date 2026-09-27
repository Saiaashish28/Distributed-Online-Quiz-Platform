package com.quizsphere.service;

import com.quizsphere.config.AppProperties;
import com.quizsphere.dto.AuthDtos.*;
import com.quizsphere.dto.StudentDtos.CourseRef;
import com.quizsphere.entity.Course;
import com.quizsphere.entity.Role;
import com.quizsphere.entity.Student;
import com.quizsphere.entity.User;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.StudentRepository;
import com.quizsphere.repository.UserRepository;
import com.quizsphere.security.AuthUser;
import com.quizsphere.security.JwtService;
import com.quizsphere.util.Text;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Optional;

@Service
public class AuthService {

    /** Used to spend equal time on unknown accounts, so response timing does not reveal them. */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z6s1YVSgPpyXhRbBf/ciO7Om";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthService(UserRepository userRepository, StudentRepository studentRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AppProperties props) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
    }

    public AuthConfig config() {
        String code = props.registration().adminInviteCode();
        return new AuthConfig(props.registration().studentSelfRegistration(), code != null && !code.isBlank());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user;
        if (req.role() == Role.ADMIN) {
            user = userRepository.findByEmailAndRole(req.identifier().trim(), Role.ADMIN).orElse(null);
        } else {
            user = studentRepository.findByRegisterNumber(Text.normalizeRegisterNumber(req.identifier()))
                    .map(Student::getUser).orElse(null);
        }
        if (user == null) {
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is deactivated. Contact your administrator.");
        }
        return issue(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        return req.role() == Role.ADMIN ? registerAdmin(req) : registerStudent(req);
    }

    private AuthResponse registerAdmin(RegisterRequest req) {
        String expected = props.registration().adminInviteCode();
        if (expected == null || expected.isBlank()) {
            throw ApiException.forbidden("Admin registration is disabled on this server");
        }
        if (req.inviteCode() == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), req.inviteCode().getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Invalid admin invite code");
        }
        String email = Text.trimToNull(req.email());
        if (email == null) {
            throw ApiException.badRequest("Email is required for admin accounts");
        }
        if (userRepository.findByEmailAndRole(email, Role.ADMIN).isPresent()) {
            throw ApiException.conflict("An admin with this email already exists");
        }
        User user = createAdmin(req.name().trim(), email, req.password());
        return issue(user);
    }

    public User createAdmin(String name, String email, String password) {
        User user = new User();
        user.setName(name);
        user.setEmail(email.toLowerCase());
        user.setRole(Role.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    private AuthResponse registerStudent(RegisterRequest req) {
        if (!props.registration().studentSelfRegistration()) {
            throw ApiException.forbidden("Student self-registration is disabled. Ask your administrator for an account.");
        }
        String regNo = Text.normalizeRegisterNumber(req.registerNumber());
        if (regNo == null || !Text.REGISTER_NUMBER.matcher(regNo).matches()) {
            throw ApiException.badRequest("A valid register number is required (3-40 letters, digits, / - _)");
        }
        if (req.academicYear() == null || Text.trimToNull(req.department()) == null) {
            throw ApiException.badRequest("Academic year and department are required");
        }
        if (studentRepository.existsByRegisterNumber(regNo)) {
            throw ApiException.conflict("This register number is already registered");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(Text.trimToNull(req.email()));
        user.setRole(Role.STUDENT);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);

        Student s = new Student();
        s.setUser(user);
        s.setRegisterNumber(regNo);
        s.setFullName(req.name().trim());
        s.setAcademicYear(req.academicYear());
        s.setDepartment(Text.upperOrNull(req.department()));
        s.setSection(Text.upperOrNull(req.section()));
        s.setProgram(Text.upperOrNull(req.program()));
        s.setSemester(req.semester());
        studentRepository.saveAndFlush(s);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserInfo me(AuthUser principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return toInfo(user);
    }

    @Transactional
    public void changePassword(AuthUser principal, ChangePasswordRequest req) {
        User user = userRepository.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw ApiException.badRequest("New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
    }

    private AuthResponse issue(User user) {
        JwtService.Issued issued = jwtService.issue(user.getId(), user.getRole());
        return new AuthResponse(issued.token(), issued.expiresAt(), toInfo(user));
    }

    private UserInfo toInfo(User user) {
        StudentProfile profile = null;
        if (user.getRole() == Role.STUDENT) {
            Optional<Student> s = studentRepository.findByUserId(user.getId())
                    .flatMap(st -> studentRepository.findWithCoursesById(st.getId()));
            profile = s.map(st -> new StudentProfile(st.getId(), st.getRegisterNumber(), st.getFullName(),
                    st.getAcademicYear(), st.getDepartment(), st.getSection(), st.getProgram(), st.getSemester(),
                    st.getCourses().stream().sorted(Comparator.comparing(Course::getCourseCode))
                            .map(CourseRef::of).toList())).orElse(null);
        }
        return new UserInfo(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.isMustChangePassword(), profile);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
