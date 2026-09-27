package com.quizsphere.security;

import com.quizsphere.entity.Role;

/** Authenticated principal derived from a validated JWT and the current user record. */
public record AuthUser(Long userId, Role role, Long studentId, String name) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
