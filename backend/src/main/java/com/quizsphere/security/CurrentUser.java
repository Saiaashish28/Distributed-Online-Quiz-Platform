package com.quizsphere.security;

import com.quizsphere.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return user;
    }

    public static Long studentId() {
        AuthUser user = get();
        if (user.studentId() == null) {
            throw ApiException.forbidden("Student account required");
        }
        return user.studentId();
    }
}
