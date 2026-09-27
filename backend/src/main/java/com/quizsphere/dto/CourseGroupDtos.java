package com.quizsphere.dto;

import com.quizsphere.entity.GroupFilter;
import com.quizsphere.entity.GroupType;
import com.quizsphere.entity.MembershipMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class CourseGroupDtos {

    private CourseGroupDtos() {
    }

    public record CourseRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9 _-]+$", message = "letters, digits, space, - and _ only")
            String courseCode,
            @NotBlank @Size(max = 150) String courseName,
            @Min(1) @Max(12) Integer semester) {
    }

    public record CourseUpdateRequest(
            @Size(min = 1, max = 150) String courseName,
            @Min(1) @Max(12) Integer semester) {
    }

    public record CourseResponse(Long id, String courseCode, String courseName, Integer semester, long studentCount,
                                 Instant createdAt) {
    }

    public record GroupRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull GroupType groupType,
            @NotNull MembershipMode membershipMode,
            @Valid GroupFilter filter,
            /* MANUAL groups: copy the students matching the filter as initial members. */
            boolean seedFromFilter,
            List<Long> studentIds) {
    }

    public record GroupUpdateRequest(
            @Size(min = 1, max = 100) String name,
            @Size(max = 500) String description,
            GroupFilter filter) {
    }

    public record GroupResponse(Long id, String name, String description, GroupType groupType,
                                MembershipMode membershipMode, GroupFilter filter, String filterSummary,
                                long memberCount, String ownerName, boolean owned, boolean usedByAssignments,
                                Instant createdAt) {
    }

    public record GroupDetail(GroupResponse group, List<StudentDtos.StudentResponse> members) {
    }

    public record PreviewRequest(@NotNull GroupFilter filter) {
    }

    public record PreviewResponse(int count, List<StudentDtos.StudentResponse> students) {
    }
}
