package com.quizsphere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base class: boots the full application against a real PostgreSQL (Testcontainers) and
 * resets all tables before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;

    protected String adminToken;

    @BeforeEach
    void resetDatabase() throws Exception {
        jdbc.execute("TRUNCATE users, students, courses, student_groups, quizzes, question_banks, assignments "
                + "RESTART IDENTITY CASCADE");
        adminToken = registerAdmin("admin@test.edu", "Admin Password1");
    }

    // ----------------------------------------------------------------- http helpers

    public record Res(int status, JsonNode body, byte[] raw) {
    }

    protected Res call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        MvcResult r = mvc.perform(req).andReturn();
        byte[] raw = r.getResponse().getContentAsByteArray();
        JsonNode node = null;
        String type = r.getResponse().getContentType();
        if (raw.length > 0 && type != null && type.contains("json")) node = json.readTree(raw);
        return new Res(r.getResponse().getStatus(), node, raw);
    }

    protected Res get(String url, String token) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url), token, null);
    }

    protected Res post(String url, String token, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), token, body);
    }

    protected Res put(String url, String token, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url), token, body);
    }

    protected Res patch(String url, String token, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), token, body);
    }

    protected Res upload(String url, String token, String filename, byte[] content) throws Exception {
        return call(multipart(url).file(new MockMultipartFile("file", filename, "application/octet-stream", content)),
                token, null);
    }

    // --------------------------------------------------------------- domain helpers

    protected String registerAdmin(String email, String password) throws Exception {
        Res r = post("/api/auth/register", null, Map.of("role", "ADMIN", "name", "Admin", "email", email,
                "password", password, "inviteCode", "test-invite"));
        return r.body().path("token").asText();
    }

    protected long createCourse(String code) throws Exception {
        return post("/api/admin/courses", adminToken, Map.of("courseCode", code, "courseName", code + " course"))
                .body().path("id").asLong();
    }

    protected long createStudent(String regNo, int year, String dept, String section, List<Long> courseIds)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("registerNumber", regNo, "fullName", "Student " + regNo,
                "academicYear", year, "department", dept, "password", "Password " + regNo));
        if (section != null) body.put("section", section);
        if (courseIds != null) body.put("courseIds", courseIds);
        Res r = post("/api/admin/students", adminToken, body);
        if (r.status() != 201) throw new AssertionError("create student failed: " + r.body());
        return r.body().path("id").asLong();
    }

    protected String studentLogin(String regNo) throws Exception {
        Res r = post("/api/auth/login", null, Map.of("role", "STUDENT", "identifier", regNo,
                "password", "Password " + regNo));
        if (r.status() != 200) throw new AssertionError("login failed: " + r.body());
        return r.body().path("token").asText();
    }

    protected Map<String, Object> question(String text, int correctIndex, double points) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            options.add(Map.of("text", text + " option " + (char) ('A' + i), "correct", i == correctIndex));
        }
        return Map.of("text", text, "points", points, "options", options);
    }

    /** Creates and publishes a quiz with the given number of questions (correct option = index 0). */
    protected long publishedQuiz(int questions) throws Exception {
        long quizId = post("/api/admin/quizzes", adminToken, Map.of("title", "Quiz", "durationMinutes", 30))
                .body().path("id").asLong();
        for (int i = 0; i < questions; i++) {
            post("/api/admin/quizzes/" + quizId + "/questions", adminToken, question("Q" + (i + 1), 0, 1 + i));
        }
        Res r = post("/api/admin/quizzes/" + quizId + "/publish", adminToken, null);
        if (r.status() != 200) throw new AssertionError("publish failed: " + r.body());
        return quizId;
    }

    protected long createAssignment(Map<String, Object> body) throws Exception {
        Res r = post("/api/admin/assignments", adminToken, body);
        if (r.status() != 201) throw new AssertionError("create assignment failed: " + r.body());
        return r.body().path("summary").path("id").asLong();
    }

    protected long dynamicGroup(String name, Map<String, Object> filter, String type) throws Exception {
        Res r = post("/api/admin/groups", adminToken, Map.of("name", name, "groupType", type,
                "membershipMode", "DYNAMIC", "filter", filter));
        if (r.status() != 201) throw new AssertionError("create group failed: " + r.body());
        return r.body().path("id").asLong();
    }

    // ------------------------------------------------------------ spreadsheet helpers

    protected static byte[] xlsx(List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sh.createRow(r);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    Object v = rows.get(r).get(c);
                    if (v instanceof Number n) row.createCell(c).setCellValue(n.doubleValue());
                    else if (v != null) row.createCell(c).setCellValue(v.toString());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    protected static XSSFWorkbook readXlsx(byte[] data) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(data));
    }
}
