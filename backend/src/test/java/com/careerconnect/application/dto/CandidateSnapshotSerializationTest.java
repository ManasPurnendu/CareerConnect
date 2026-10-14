package com.careerconnect.application.dto;

import com.careerconnect.application.service.ApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateSnapshotSerializationTest {

    private ObjectMapper objectMapper;
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        applicationService = new ApplicationService(null, null, null, null, null, null, null, null, objectMapper);
    }

    @Test
    @DisplayName("P5.4-Unit1: CandidateSnapshot full serialization & deserialization round-trip")
    void candidateSnapshot_fullSerializationRoundTrip() throws Exception {
        CandidateSnapshot.CandidateEducationSnapshot edu = new CandidateSnapshot.CandidateEducationSnapshot(
                1L, "Stanford University", "B.S.", "Computer Science", 2018, 2022, "3.9", 1);
        CandidateSnapshot.CandidateProjectSnapshot proj = new CandidateSnapshot.CandidateProjectSnapshot(
                2L, "Distributed Cache", "High performance key-value store", "https://github.com/test/cache", 1);
        CandidateSnapshot.CandidateExperienceSnapshot exp = new CandidateSnapshot.CandidateExperienceSnapshot(
                3L, "TechCorp", "SWE Intern", "2021-06-01", "2021-08-31", false, "Built distributed services", 1);

        CandidateSnapshot snapshot = new CandidateSnapshot(
                100L,
                "Alice Smith",
                "alice@example.com",
                "Software Engineer",
                "San Francisco, CA",
                "Passionate about distributed systems.",
                List.of(edu),
                List.of("Java", "Spring Boot", "SQL"),
                List.of(proj),
                List.of(exp)
        );

        String json = objectMapper.writeValueAsString(snapshot);
        assertThat(json).isNotNull().isNotEmpty();

        CandidateSnapshot parsed = applicationService.parseCandidateSnapshot(json);
        assertThat(parsed).isNotNull();
        assertThat(parsed.candidateId()).isEqualTo(100L);
        assertThat(parsed.fullName()).isEqualTo("Alice Smith");
        assertThat(parsed.email()).isEqualTo("alice@example.com");
        assertThat(parsed.headline()).isEqualTo("Software Engineer");
        assertThat(parsed.location()).isEqualTo("San Francisco, CA");
        assertThat(parsed.summary()).isEqualTo("Passionate about distributed systems.");

        assertThat(parsed.education()).hasSize(1);
        assertThat(parsed.education().get(0).institution()).isEqualTo("Stanford University");
        assertThat(parsed.education().get(0).degree()).isEqualTo("B.S.");
        assertThat(parsed.education().get(0).fieldOfStudy()).isEqualTo("Computer Science");
        assertThat(parsed.education().get(0).startYear()).isEqualTo(2018);
        assertThat(parsed.education().get(0).endYear()).isEqualTo(2022);
        assertThat(parsed.education().get(0).grade()).isEqualTo("3.9");
        assertThat(parsed.education().get(0).sortOrder()).isEqualTo(1);

        assertThat(parsed.skills()).containsExactly("Java", "Spring Boot", "SQL");

        assertThat(parsed.projects()).hasSize(1);
        assertThat(parsed.projects().get(0).title()).isEqualTo("Distributed Cache");
        assertThat(parsed.projects().get(0).description()).isEqualTo("High performance key-value store");
        assertThat(parsed.projects().get(0).projectUrl()).isEqualTo("https://github.com/test/cache");
        assertThat(parsed.projects().get(0).sortOrder()).isEqualTo(1);

        assertThat(parsed.experience()).hasSize(1);
        assertThat(parsed.experience().get(0).companyName()).isEqualTo("TechCorp");
        assertThat(parsed.experience().get(0).roleTitle()).isEqualTo("SWE Intern");
        assertThat(parsed.experience().get(0).startDate()).isEqualTo("2021-06-01");
        assertThat(parsed.experience().get(0).endDate()).isEqualTo("2021-08-31");
        assertThat(parsed.experience().get(0).isCurrent()).isFalse();
        assertThat(parsed.experience().get(0).description()).isEqualTo("Built distributed services");
        assertThat(parsed.experience().get(0).sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("P5.4-Unit2: ResumeSnapshot full serialization & deserialization round-trip")
    void resumeSnapshot_fullSerializationRoundTrip() throws Exception {
        ResumeSnapshot resume = new ResumeSnapshot(
                55L, "alice_resume.pdf", "resumes/uuid-55.pdf", "application/pdf", 102400L);

        String json = objectMapper.writeValueAsString(resume);
        assertThat(json).isNotNull().isNotEmpty();

        ResumeSnapshot parsed = applicationService.parseResumeSnapshot(json);
        assertThat(parsed).isNotNull();
        assertThat(parsed.id()).isEqualTo(55L);
        assertThat(parsed.originalFilename()).isEqualTo("alice_resume.pdf");
        assertThat(parsed.storageKey()).isEqualTo("resumes/uuid-55.pdf");
        assertThat(parsed.contentType()).isEqualTo("application/pdf");
        assertThat(parsed.sizeBytes()).isEqualTo(102400L);
    }

    @Test
    @DisplayName("P5.4-Unit3: CandidateSnapshot preserves null optional fields without fake placeholders")
    void candidateSnapshot_preservesNullFields() throws Exception {
        CandidateSnapshot.CandidateEducationSnapshot edu = new CandidateSnapshot.CandidateEducationSnapshot(
                1L, "Public University", "B.A.", null, 2020, null, null, 1);
        CandidateSnapshot.CandidateProjectSnapshot proj = new CandidateSnapshot.CandidateProjectSnapshot(
                2L, "CLI Tool", "A command-line tool", null, 1);
        CandidateSnapshot.CandidateExperienceSnapshot exp = new CandidateSnapshot.CandidateExperienceSnapshot(
                3L, "Startup", "Founder", "2023-01-01", null, true, null, 1);

        CandidateSnapshot snapshot = new CandidateSnapshot(
                200L,
                "Bob Jones",
                null,
                null,
                null,
                null,
                List.of(edu),
                List.of(),
                List.of(proj),
                List.of(exp)
        );

        String json = objectMapper.writeValueAsString(snapshot);
        CandidateSnapshot parsed = applicationService.parseCandidateSnapshot(json);

        assertThat(parsed.email()).isNull();
        assertThat(parsed.headline()).isNull();
        assertThat(parsed.location()).isNull();
        assertThat(parsed.summary()).isNull();

        assertThat(parsed.education().get(0).fieldOfStudy()).isNull();
        assertThat(parsed.education().get(0).endYear()).isNull();
        assertThat(parsed.education().get(0).grade()).isNull();

        assertThat(parsed.projects().get(0).projectUrl()).isNull();

        assertThat(parsed.experience().get(0).endDate()).isNull();
        assertThat(parsed.experience().get(0).isCurrent()).isTrue();
        assertThat(parsed.experience().get(0).description()).isNull();
    }

    @Test
    @DisplayName("P5.4-Unit4: Deserialization tolerates unknown fields for forward compatibility")
    void snapshot_toleratesUnknownFields() {
        String jsonWithExtraFields = """
                {
                    "candidateId": 300,
                    "fullName": "Carol Danvers",
                    "futureField": "someValue",
                    "education": [],
                    "skills": [],
                    "projects": [],
                    "experience": []
                }
                """;

        CandidateSnapshot parsed = applicationService.parseCandidateSnapshot(jsonWithExtraFields);
        assertThat(parsed).isNotNull();
        assertThat(parsed.candidateId()).isEqualTo(300L);
        assertThat(parsed.fullName()).isEqualTo("Carol Danvers");
    }

    @Test
    @DisplayName("P5.4-Unit5: Parser handles textual-escaped JSON string correctly")
    void parser_handlesTextualEscapedJson() throws Exception {
        CandidateSnapshot original = new CandidateSnapshot(
                400L, "Dave Miller", "dave@example.com", null, null, null,
                List.of(), List.of("Go"), List.of(), List.of());

        // In H2 JSON columns, rs.getString returns the JSON wrapped as a JSON string scalar
        String rawJson = objectMapper.writeValueAsString(original);
        String escapedJson = objectMapper.writeValueAsString(rawJson);

        CandidateSnapshot parsed = applicationService.parseCandidateSnapshot(escapedJson);
        assertThat(parsed).isNotNull();
        assertThat(parsed.candidateId()).isEqualTo(400L);
        assertThat(parsed.fullName()).isEqualTo("Dave Miller");
        assertThat(parsed.skills()).containsExactly("Go");
    }

    @Test
    @DisplayName("P5.4-Unit6: Null or blank JSON string returns null cleanly")
    void parser_handlesNullAndBlank() {
        assertThat(applicationService.parseCandidateSnapshot(null)).isNull();
        assertThat(applicationService.parseCandidateSnapshot("")).isNull();
        assertThat(applicationService.parseCandidateSnapshot("   ")).isNull();

        assertThat(applicationService.parseResumeSnapshot(null)).isNull();
        assertThat(applicationService.parseResumeSnapshot("")).isNull();
        assertThat(applicationService.parseResumeSnapshot("   ")).isNull();
    }

    @Test
    @DisplayName("P5.8-Snap-Escaped: CandidateSnapshot handles escaped quotes, newlines, and unicode safely")
    void snapshot_handlesEscapedAndUnicodeStrings() throws Exception {
        CandidateSnapshot.CandidateProjectSnapshot proj = new CandidateSnapshot.CandidateProjectSnapshot(
                1L, "Project \"Alpha\\Beta\"", "Line 1\nLine 2\tTabbed\r\nSpecial: \u2605 \u00E9 \uD83D\uDE80", "https://example.com/\"test\"", 1);

        CandidateSnapshot snapshot = new CandidateSnapshot(
                500L,
                "Ren\u00E9 O'Connor-Smith",
                "rene@example.com",
                "Lead \"Architect\"\n(Backend)",
                "Montr\u00E9al, Qu\u00E9bec",
                "Summary with \"quotes\", \nnewlines and unicode: \u2705",
                List.of(),
                List.of("C++", "C#", "Node.js (v20+)"),
                List.of(proj),
                List.of()
        );

        String json = objectMapper.writeValueAsString(snapshot);
        CandidateSnapshot parsed = applicationService.parseCandidateSnapshot(json);

        assertThat(parsed).isNotNull();
        assertThat(parsed.fullName()).isEqualTo("Ren\u00E9 O'Connor-Smith");
        assertThat(parsed.headline()).isEqualTo("Lead \"Architect\"\n(Backend)");
        assertThat(parsed.location()).isEqualTo("Montr\u00E9al, Qu\u00E9bec");
        assertThat(parsed.summary()).isEqualTo("Summary with \"quotes\", \nnewlines and unicode: \u2705");
        assertThat(parsed.projects().get(0).title()).isEqualTo("Project \"Alpha\\Beta\"");
        assertThat(parsed.projects().get(0).description()).isEqualTo("Line 1\nLine 2\tTabbed\r\nSpecial: \u2605 \u00E9 \uD83D\uDE80");
        assertThat(parsed.skills()).containsExactly("C++", "C#", "Node.js (v20+)");
    }

    @Test
    @DisplayName("P5.8-Snap-Empty: CandidateSnapshot handles empty collections deterministically")
    void snapshot_handlesEmptyCollectionsDeterministically() throws Exception {
        CandidateSnapshot emptyCollections = new CandidateSnapshot(
                600L, "Empty Collections Cand", "empty@example.com", null, null, null,
                List.of(), List.of(), List.of(), List.of());

        String json = objectMapper.writeValueAsString(emptyCollections);
        CandidateSnapshot parsed = applicationService.parseCandidateSnapshot(json);

        assertThat(parsed).isNotNull();
        assertThat(parsed.education()).isEmpty();
        assertThat(parsed.skills()).isEmpty();
        assertThat(parsed.projects()).isEmpty();
        assertThat(parsed.experience()).isEmpty();
    }
}
