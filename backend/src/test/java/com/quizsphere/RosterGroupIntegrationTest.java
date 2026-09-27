package com.quizsphere;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RosterGroupIntegrationTest extends IntegrationTest {

    private static final List<Object> HEADER = List.of("Register Number", "Student Name", "Email", "Academic Year",
            "Department", "Section", "Semester", "Course Codes");

    @Test
    void rosterImportPreviewsValidatesAndCommits() throws Exception {
        createCourse("CS301");
        byte[] file = xlsx(List.of(HEADER,
                List.of("21CSE001", "Asha", "asha@x.edu", "III", "CSE", "A", 5, "CS301"),
                List.of("21CSE002", "Bala", "", "3", "CSE", "B", 5, ""),
                List.of("21CSE001", "Dup", "", "III", "CSE", "A", 5, ""),
                List.of("22ECE001", "Chitra", "bad-email", "II", "ECE", "A", 3, "NOPE")));
        Res preview = upload("/api/admin/students/import", adminToken, "roster.xlsx", file);
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.body().path("toCreate").asInt()).isEqualTo(2);
        assertThat(preview.body().path("invalid").asInt()).isEqualTo(2);
        JsonNode rows = preview.body().path("rows");
        assertThat(rows.get(2).path("errors").toString()).contains("Duplicate register number");
        assertThat(rows.get(3).path("errors").toString()).contains("Email is not valid").contains("Unknown course code");
        // nothing was written by the preview
        assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isZero();

        // commit is refused while invalid rows exist, unless explicitly skipped
        assertThat(upload("/api/admin/students/import?mode=commit", adminToken, "roster.xlsx", file).status()).isEqualTo(422);
        Res commit = upload("/api/admin/students/import?mode=commit&skipInvalid=true", adminToken, "roster.xlsx", file);
        assertThat(commit.body().path("committed").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student_courses", Integer.class)).isEqualTo(1);
    }

    @Test
    void existingStudentsAreNeverSilentlyOverwritten() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        byte[] file = xlsx(List.of(HEADER, List.of("21cse001", "Renamed", "", "IV", "IT", "C", 7, "")));
        Res skip = upload("/api/admin/students/import?mode=commit", adminToken, "r.xlsx", file);
        assertThat(skip.body().path("skippedExisting").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select full_name from students", String.class)).isEqualTo("Student 21CSE001");

        Res update = upload("/api/admin/students/import?mode=commit&updateExisting=true", adminToken, "r.xlsx", file);
        assertThat(update.body().path("toUpdate").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select full_name from students", String.class)).isEqualTo("Renamed");
    }

    @Test
    void csvRosterImportAndHeaderValidation() throws Exception {
        String csv = "﻿Register Number,Student Name,Academic Year,Department\n21CSE010,\"Kumar, R\",3,CSE\n";
        Res ok = upload("/api/admin/students/import?mode=commit", adminToken, "r.csv", csv.getBytes());
        assertThat(ok.body().path("toCreate").asInt()).isEqualTo(1);
        Res bad = upload("/api/admin/students/import", adminToken, "r.csv", "Name,Dept\nA,B\n".getBytes());
        assertThat(bad.body().path("headerErrors").size()).isGreaterThan(0);
        assertThat(upload("/api/admin/students/import", adminToken, "r.txt", "x".getBytes()).status()).isEqualTo(400);
    }

    @Test
    void duplicateRegisterNumberIsRejected() throws Exception {
        createStudent("21CSE001", 3, "CSE", "A", null);
        Res r = post("/api/admin/students", adminToken, Map.of("registerNumber", "21cse001", "fullName", "X",
                "academicYear", 3, "department", "CSE"));
        assertThat(r.status()).isEqualTo(409);
    }

    @Test
    void courseEnrollmentAndDynamicGroups() throws Exception {
        long dcc = createCourse("DCC");
        long a = createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("21CSE002", 3, "CSE", "B", null);
        createStudent("22ECE001", 2, "ECE", "A", null);
        createStudent("22ECE002", 2, "ECE", "B", null);

        Res enroll = post("/api/admin/courses/" + dcc + "/students", adminToken,
                Map.of("registerNumbers", List.of("21CSE001", "21CSE001", "NOPE")));
        assertThat(enroll.body().path("added").asInt()).isEqualTo(1);
        assertThat(enroll.body().path("notFound").toString()).contains("NOPE");
        Res again = post("/api/admin/courses/" + dcc + "/students", adminToken, Map.of("studentIds", List.of(a)));
        assertThat(again.body().path("alreadyPresent").asInt()).isEqualTo(1);

        long iiiCse = dynamicGroup("III-CSE", Map.of("academicYears", List.of(3), "departments", List.of("cse")), "ACADEMIC");
        long iiEceA = dynamicGroup("II-ECE-A", Map.of("academicYears", List.of(2), "departments", List.of("ECE"),
                "sections", List.of("A")), "ACADEMIC");
        long courseGroup = dynamicGroup("III-CSE-DCC", Map.of("academicYears", List.of(3), "courseIds", List.of(dcc)),
                "COURSE_BASED");
        assertThat(get("/api/admin/groups/" + iiiCse, adminToken).body().path("members").size()).isEqualTo(2);
        assertThat(get("/api/admin/groups/" + iiEceA, adminToken).body().path("members").get(0)
                .path("registerNumber").asText()).isEqualTo("22ECE001");
        assertThat(get("/api/admin/groups/" + courseGroup, adminToken).body().path("members").size()).isEqualTo(1);

        // dynamic membership follows attribute changes
        patch("/api/admin/students/" + a, adminToken, Map.of("section", "C", "academicYear", 4));
        assertThat(get("/api/admin/groups/" + iiiCse, adminToken).body().path("members").size()).isEqualTo(1);

        // validation: academic dynamic group needs academic criteria; academic groups cannot filter courses
        assertThat(post("/api/admin/groups", adminToken, Map.of("name", "bad", "groupType", "ACADEMIC",
                "membershipMode", "DYNAMIC", "filter", Map.of("courseIds", List.of(dcc)))).status()).isEqualTo(400);
        // duplicate group name
        assertThat(post("/api/admin/groups", adminToken, Map.of("name", "iii-cse", "groupType", "CUSTOM",
                "membershipMode", "MANUAL")).status()).isEqualTo(409);
    }

    @Test
    void manualGroupMembershipPreventsDuplicates() throws Exception {
        long s1 = createStudent("21CSE001", 3, "CSE", "A", null);
        createStudent("21CSE002", 3, "CSE", "A", null);
        Res preview = post("/api/admin/groups/preview", adminToken, Map.of("filter", Map.of("sections", List.of("A"))));
        assertThat(preview.body().path("count").asInt()).isEqualTo(2);

        long g = post("/api/admin/groups", adminToken, Map.of("name", "Project team", "groupType", "CUSTOM",
                "membershipMode", "MANUAL", "studentIds", List.of(s1))).body().path("id").asLong();
        Res add = post("/api/admin/groups/" + g + "/members", adminToken,
                Map.of("registerNumbers", List.of("21CSE001", "21CSE002")));
        assertThat(add.body().path("added").asInt()).isEqualTo(1);
        assertThat(add.body().path("alreadyPresent").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from group_members", Integer.class)).isEqualTo(2);

        assertThat(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/groups/" + g + "/members/" + s1), adminToken, null).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from group_members", Integer.class)).isEqualTo(1);
    }
}
