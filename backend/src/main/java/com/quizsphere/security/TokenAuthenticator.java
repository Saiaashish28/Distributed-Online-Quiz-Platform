package com.quizsphere.security;

import com.quizsphere.entity.Role;
import com.quizsphere.entity.Student;
import com.quizsphere.entity.User;
import com.quizsphere.repository.StudentRepository;
import com.quizsphere.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves a bearer token to an {@link AuthUser}. The role and identity come from the
 * database record, not from client input; deactivated accounts are rejected immediately.
 */
@Component
public class TokenAuthenticator {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    public TokenAuthenticator(JwtService jwtService, UserRepository userRepository, StudentRepository studentRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AuthUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Claims> claims = jwtService.parse(token);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        long userId;
        try {
            userId = Long.parseLong(claims.get().getSubject());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        Optional<User> user = userRepository.findById(userId).filter(User::isActive);
        if (user.isEmpty() || !user.get().getRole().name().equals(claims.get().get("role", String.class))) {
            return Optional.empty();
        }
        Long studentId = null;
        if (user.get().getRole() == Role.STUDENT) {
            Optional<Student> student = studentRepository.findByUserId(userId).filter(Student::isActive);
            if (student.isEmpty()) {
                return Optional.empty();
            }
            studentId = student.get().getId();
        }
        return Optional.of(new AuthUser(userId, user.get().getRole(), studentId, user.get().getName()));
    }
}
