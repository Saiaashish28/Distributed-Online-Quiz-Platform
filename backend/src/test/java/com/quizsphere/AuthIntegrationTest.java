package com.quizsphere;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void healthEndpointIsPublic() throws Exception {
        Res r = get("/health", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("database").asText()).isEqualTo("UP");
    }

    @Test
    void adminLoginAndMe() throws Exception {
        Res login = post("/api/auth/login", null, Map.of("role", "ADMIN", "identifier", "ADMIN@test.edu",
                "password", "Admin Password1"));
        assertThat(login.status()).isEqualTo(200);
        Res me = get("/api/auth/me", login.body().path("token").asText());
        assertThat(me.body().path("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void wrongPasswordAndUnknownUserAreRejectedIdentically() throws Exception {
        Res wrong = post("/api/auth/login", null, Map.of("role", "ADMIN", "identifier", "admin@test.edu", "password", "nope-nope"));
        Res unknown = post("/api/auth/login", null, Map.of("role", "ADMIN", "identifier", "x@test.edu", "password", "nope-nope"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.body().path("message").asText()).isEqualTo(unknown.body().path("message").asText());
    }

    @Test
    void protectedRoutesRequireTokenAndRole() throws Exception {
        assertThat(get("/api/admin/students", null).status()).isEqualTo(401);
        assertThat(get("/api/admin/students", "garbage.token.value").status()).isEqualTo(401);
        createStudent("21CSE001", 3, "CSE", "A", null);
        String student = studentLogin("21CSE001");
        assertThat(get("/api/admin/students", student).status()).isEqualTo(403);
        assertThat(get("/api/student/assignments", adminToken).status()).isEqualTo(403);
        assertThat(get("/api/student/assignments", student).status()).isEqualTo(200);
    }

    @Test
    void adminRegistrationNeedsInviteCode() throws Exception {
        Res r = post("/api/auth/register", null, Map.of("role", "ADMIN", "name", "X", "email", "x@test.edu",
                "password", "Password123", "inviteCode", "wrong"));
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void studentSelfRegistrationEnforcesUniqueRegisterNumber() throws Exception {
        Map<String, Object> body = Map.of("role", "STUDENT", "name", "Asha", "password", "Password123",
                "registerNumber", "21cse777", "academicYear", 3, "department", "cse");
        Res first = post("/api/auth/register", null, body);
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body().path("user").path("student").path("registerNumber").asText()).isEqualTo("21CSE777");
        Res dup = post("/api/auth/register", null, Map.of("role", "STUDENT", "name", "Other", "password", "Password123",
                "registerNumber", " 21CSE777 ", "academicYear", 2, "department", "ECE"));
        assertThat(dup.status()).isEqualTo(409);
    }

    @Test
    void deactivatedStudentLosesAccessImmediately() throws Exception {
        long id = createStudent("21CSE002", 3, "CSE", "A", null);
        String token = studentLogin("21CSE002");
        patch("/api/admin/students/" + id, adminToken, Map.of("active", false));
        assertThat(get("/api/student/assignments", token).status()).isEqualTo(401);
        Res login = post("/api/auth/login", null, Map.of("role", "STUDENT", "identifier", "21CSE002",
                "password", "Password 21CSE002"));
        assertThat(login.status()).isEqualTo(403);
    }

    @Test
    void changePasswordClearsMustChangeFlag() throws Exception {
        createStudent("21CSE003", 3, "CSE", "A", null);
        String token = studentLogin("21CSE003");
        assertThat(get("/api/auth/me", token).body().path("mustChangePassword").asBoolean()).isTrue();
        Res r = post("/api/auth/change-password", token, Map.of("currentPassword", "Password 21CSE003",
                "newPassword", "BrandNewPass1"));
        assertThat(r.status()).isEqualTo(204);
        assertThat(get("/api/auth/me", token).body().path("mustChangePassword").asBoolean()).isFalse();
    }
}
