package com.quizsphere.controller;

import com.quizsphere.config.AppProperties;
import com.quizsphere.dto.AuthDtos.*;
import com.quizsphere.security.CurrentUser;
import com.quizsphere.security.RateLimiter;
import com.quizsphere.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final AppProperties props;

    public AuthController(AuthService authService, RateLimiter rateLimiter, AppProperties props) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @GetMapping("/config")
    public AuthConfig config() {
        return authService.config();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        int limit = props.rateLimit().loginPerMinute();
        rateLimiter.check("login-ip:" + http.getRemoteAddr(), limit * 5);
        rateLimiter.check("login:" + req.identifier().trim().toLowerCase(Locale.ROOT), limit);
        return authService.login(req);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        rateLimiter.check("register-ip:" + http.getRemoteAddr(), props.rateLimit().loginPerMinute());
        return authService.register(req);
    }

    @GetMapping("/me")
    public UserInfo me() {
        return authService.me(CurrentUser.get());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(CurrentUser.get(), req);
    }
}
