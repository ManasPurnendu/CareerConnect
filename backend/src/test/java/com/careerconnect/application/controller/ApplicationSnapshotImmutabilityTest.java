package com.careerconnect.application.controller;

import com.careerconnect.application.dto.CandidateSnapshot;
import com.careerconnect.application.dto.ResumeSnapshot;
import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.application.service.ApplicationService;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationSnapshotImmutabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationService applicationService;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private record UserAuth(String token, Long candidateId, Long userId) {}

    private UserAuth registerCandidate(String email, String name) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, name);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new UserAuth(token, candidateId, userId);
    }

    private Long addEducation(String token, String inst, String degree, String field, int start, int end, int order) throws Exception {
        CreateEducationRequest edu = new CreateEducationRequest(inst, degree, field, start, end, "3.8", order);
        MvcResult res = mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edu)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private Long addSkill(String token, String name) throws Exception {
        AssignSkillRequest skill = new AssignSkillRequest(name);
        MvcResult res = mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skill)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private Long addProject(String token, String title, String desc, String url, int order) throws Exception {
        CreateProjectRequest proj = new CreateProjectRequest(title, desc, url, order);
        MvcResult res = mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proj)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private Long addExperience(String token, String company, String role, String start, String end, boolean isCurrent, String desc, int order) throws Exception {
        CreateExperienceRequest exp = new CreateExperienceRequest(
                company, role, LocalDate.parse(start), end != null ? LocalDate.parse(end) : null, isCurrent, desc, order);
        MvcResult res = mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exp)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private void uploadResume(String token, String filename) throws Exception {
        byte[] pdfBytes = ("%PDF-1.4 content of " + filename).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String token, String fullName, String headline, String location, String summary) throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(fullName, headline, location, summary);
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private Long createActiveJob(String suffix) {
        String companyName = "SnapshotCo " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'INTERNSHIP', 'FRESHER', 'ONSITE', ?, 'ACTIVE')
                """, companyId, "Job " + suffix, "Description " + suffix, deadline);

        return jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = ?", Long.class, "Job " + suffix);
    }

    private Long applyToJob(String token, Long jobId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Original Cover Letter\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    private CandidateSnapshot getApplicationCandidateSnapshot(Long applicationId) {
        String raw = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, applicationId);
        return applicationService.parseCandidateSnapshot(raw);
    }

    private ResumeSnapshot getApplicationResumeSnapshot(Long applicationId) {
        String raw = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, applicationId);
        return applicationService.parseResumeSnapshot(raw);
    }

    @AfterEach
    void tearDown() throws IOException {
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM experience");
        jdbcTemplate.execute("DELETE FROM projects");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM companies");

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P5.4-1: Profile mutation after application does NOT alter stored candidate_snapshot")
    void profileMutation_doesNotMutateSnapshot() throws Exception {
        UserAuth candidate = registerCandidate("p54.prof@example.com", "Original Profile Name");
        updateProfile(candidate.token(), "Original Profile Name", "Junior Dev", "Bengaluru", "Original Summary");
        addEducation(candidate.token(), "Original Univ", "B.Tech", "CS", 2018, 2022, 1);
        addSkill(candidate.token(), "Java");
        uploadResume(candidate.token(), "resume_original.pdf");

        Long jobId = createActiveJob("prof1");
        Long appId = applyToJob(candidate.token(), jobId);

        // Verify initial snapshot state
        CandidateSnapshot initialSnap = getApplicationCandidateSnapshot(appId);
        assertThat(initialSnap.fullName()).isEqualTo("Original Profile Name");
        assertThat(initialSnap.headline()).isEqualTo("Junior Dev");
        assertThat(initialSnap.location()).isEqualTo("Bengaluru");
        assertThat(initialSnap.summary()).isEqualTo("Original Summary");

        // Mutate candidate profile
        updateProfile(candidate.token(), "Mutated Profile Name", "Senior Architect", "San Francisco", "Mutated Summary");

        // Stored snapshot must remain unchanged
        CandidateSnapshot afterMutationSnap = getApplicationCandidateSnapshot(appId);
        assertThat(afterMutationSnap.fullName()).isEqualTo("Original Profile Name");
        assertThat(afterMutationSnap.headline()).isEqualTo("Junior Dev");
        assertThat(afterMutationSnap.location()).isEqualTo("Bengaluru");
        assertThat(afterMutationSnap.summary()).isEqualTo("Original Summary");
    }

    @Test
    @DisplayName("P5.4-2: Education mutation after application does NOT alter stored candidate_snapshot")
    void educationMutation_doesNotMutateSnapshot() throws Exception {
        UserAuth candidate = registerCandidate("p54.edu@example.com", "Edu Candidate");
        updateProfile(candidate.token(), "Edu Candidate", "Dev", "Delhi", "Summary");
        Long edu1 = addEducation(candidate.token(), "Original Institute", "B.E.", "IT", 2017, 2021, 1);
        addSkill(candidate.token(), "Java");
        uploadResume(candidate.token(), "resume_edu.pdf");

        Long jobId = createActiveJob("edu1");
        Long appId = applyToJob(candidate.token(), jobId);

        // Candidate adds a second education and deletes the first
        addEducation(candidate.token(), "New Masters University", "M.Tech", "AI", 2021, 2023, 2);
        mockMvc.perform(delete("/api/candidate/education/{id}", edu1)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());

        // Stored snapshot must still have only Original Institute and NOT New Masters University
        CandidateSnapshot snap = getApplicationCandidateSnapshot(appId);
        assertThat(snap.education()).hasSize(1);
        assertThat(snap.education().get(0).institution()).isEqualTo("Original Institute");
        assertThat(snap.education().get(0).degree()).isEqualTo("B.E.");
    }

    @Test
    @DisplayName("P5.4-3: Skill mutation after application does NOT alter stored candidate_snapshot")
    void skillMutation_doesNotMutateSnapshot() throws Exception {
        UserAuth candidate = registerCandidate("p54.skill@example.com", "Skill Candidate");
        updateProfile(candidate.token(), "Skill Candidate", "Dev", "Delhi", "Summary");
        addEducation(candidate.token(), "Institute", "B.E.", "IT", 2017, 2021, 1);
        Long javaSkillId = addSkill(candidate.token(), "Java");
        addSkill(candidate.token(), "SQL");
        uploadResume(candidate.token(), "resume_skill.pdf");

        Long jobId = createActiveJob("skill1");
        Long appId = applyToJob(candidate.token(), jobId);

        // Candidate adds "Python" and removes "Java"
        addSkill(candidate.token(), "Python");
        mockMvc.perform(delete("/api/candidate/skills/{id}", javaSkillId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());

        // Snapshot must still contain ["Java", "SQL"] and NOT "Python"
        CandidateSnapshot snap = getApplicationCandidateSnapshot(appId);
        assertThat(snap.skills()).containsExactlyInAnyOrder("Java", "SQL");
        assertThat(snap.skills()).doesNotContain("Python");
    }

    @Test
    @DisplayName("P5.4-4: Project mutation after application does NOT alter stored candidate_snapshot")
    void projectMutation_doesNotMutateSnapshot() throws Exception {
        UserAuth candidate = registerCandidate("p54.proj@example.com", "Proj Candidate");
        updateProfile(candidate.token(), "Proj Candidate", "Dev", "Delhi", "Summary");
        addEducation(candidate.token(), "Institute", "B.E.", "IT", 2017, 2021, 1);
        addSkill(candidate.token(), "Java");
        Long proj1 = addProject(candidate.token(), "Original Project", "Original Project Desc", "https://original.com", 1);
        uploadResume(candidate.token(), "resume_proj.pdf");

        Long jobId = createActiveJob("proj1");
        Long appId = applyToJob(candidate.token(), jobId);

        // Candidate deletes Project 1 and adds Project 2
        mockMvc.perform(delete("/api/candidate/projects/{id}", proj1)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        addProject(candidate.token(), "New Brand Project", "Brand New Desc", "https://new.com", 1);

        // Snapshot must still have Original Project
        CandidateSnapshot snap = getApplicationCandidateSnapshot(appId);
        assertThat(snap.projects()).hasSize(1);
        assertThat(snap.projects().get(0).title()).isEqualTo("Original Project");
        assertThat(snap.projects().get(0).projectUrl()).isEqualTo("https://original.com");
    }

    @Test
    @DisplayName("P5.4-5: Experience mutation after application does NOT alter stored candidate_snapshot")
    void experienceMutation_doesNotMutateSnapshot() throws Exception {
        UserAuth candidate = registerCandidate("p54.exp@example.com", "Exp Candidate");
        updateProfile(candidate.token(), "Exp Candidate", "Dev", "Delhi", "Summary");
        addEducation(candidate.token(), "Institute", "B.E.", "IT", 2017, 2021, 1);
        addSkill(candidate.token(), "Java");
        Long exp1 = addExperience(candidate.token(), "Original Corp", "Software Intern", "2020-01-01", "2020-06-30", false, "Built features", 1);
        uploadResume(candidate.token(), "resume_exp.pdf");

        Long jobId = createActiveJob("exp1");
        Long appId = applyToJob(candidate.token(), jobId);

        // Candidate deletes Experience 1 and adds Experience 2
        mockMvc.perform(delete("/api/candidate/experience/{id}", exp1)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        addExperience(candidate.token(), "New Enterprise", "Lead Architect", "2021-01-01", null, true, "Led teams", 1);

        // Snapshot must still have Original Corp
        CandidateSnapshot snap = getApplicationCandidateSnapshot(appId);
        assertThat(snap.experience()).hasSize(1);
        assertThat(snap.experience().get(0).companyName()).isEqualTo("Original Corp");
        assertThat(snap.experience().get(0).roleTitle()).isEqualTo("Software Intern");
    }

    @Test
    @DisplayName("P5.4-6: Resume replacement does NOT mutate the original application resume_snapshot")
    void resumeReplacement_doesNotMutateSnapshot() throws Exception {
        UserAuth candidate = registerCandidate("p54.resume@example.com", "Resume Candidate");
        updateProfile(candidate.token(), "Resume Candidate", "Dev", "Delhi", "Summary");
        addEducation(candidate.token(), "Institute", "B.E.", "IT", 2017, 2021, 1);
        addSkill(candidate.token(), "Java");
        uploadResume(candidate.token(), "resume_A.pdf");

        Long jobId = createActiveJob("resumerep1");
        Long appId = applyToJob(candidate.token(), jobId);

        ResumeSnapshot initialResume = getApplicationResumeSnapshot(appId);
        assertThat(initialResume.originalFilename()).isEqualTo("resume_A.pdf");
        String initialStorageKey = initialResume.storageKey();
        assertThat(initialStorageKey).isNotBlank();

        // Candidate replaces Resume A with Resume B
        uploadResume(candidate.token(), "resume_B.pdf");

        // Stored resume_snapshot in application must remain Resume A with initial storageKey
        ResumeSnapshot afterUploadResume = getApplicationResumeSnapshot(appId);
        assertThat(afterUploadResume.originalFilename()).isEqualTo("resume_A.pdf");
        assertThat(afterUploadResume.storageKey()).isEqualTo(initialStorageKey);
    }

    @Test
    @DisplayName("P5.4-7: Complete historical consistency test (Section 11 full workflow)")
    void completeHistoricalConsistencyTest() throws Exception {
        // 1. Create candidate
        UserAuth candidate = registerCandidate("p54.historic@example.com", "Historical Candidate");

        // 2. Complete candidate profile
        updateProfile(candidate.token(), "Historical Candidate", "Full Stack Intern", "Hyderabad", "Historical summary of candidate.");

        // 3. Add education
        Long eduId = addEducation(candidate.token(), "Historical University", "B.Tech", "ECE", 2018, 2022, 1);

        // 4. Add skills
        Long javaId = addSkill(candidate.token(), "Java");
        addSkill(candidate.token(), "PostgreSQL");

        // 5. Add project
        Long projId = addProject(candidate.token(), "Historical Project", "Historic Description", "https://historic.org", 1);

        // 6. Add experience
        Long expId = addExperience(candidate.token(), "Historic Labs", "Research Intern", "2021-01-01", "2021-06-30", false, "Historic work", 1);

        // 7. Upload resume A
        uploadResume(candidate.token(), "resume_historic_A.pdf");

        // 8. Apply to Job A
        Long jobId = createActiveJob("historic1");
        Long appId = applyToJob(candidate.token(), jobId);

        // 9. Capture the persisted candidate_snapshot and resume_snapshot
        CandidateSnapshot snapAtStep8 = getApplicationCandidateSnapshot(appId);
        ResumeSnapshot resumeAtStep8 = getApplicationResumeSnapshot(appId);

        // 10. Modify candidate profile
        updateProfile(candidate.token(), "Radically Mutated Name", "VP of Engineering", "New York", "Completely rewritten summary.");

        // 11. Modify education
        mockMvc.perform(delete("/api/candidate/education/{id}", eduId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        addEducation(candidate.token(), "Future Ivy League", "Ph.D.", "Quantum Computing", 2022, 2026, 1);

        // 12. Remove/change skills
        mockMvc.perform(delete("/api/candidate/skills/{id}", javaId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        addSkill(candidate.token(), "Rust");

        // 13. Modify project
        mockMvc.perform(delete("/api/candidate/projects/{id}", projId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        addProject(candidate.token(), "Futuristic Project", "Future description", "https://future.io", 1);

        // 14. Modify experience
        mockMvc.perform(delete("/api/candidate/experience/{id}", expId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNoContent());
        addExperience(candidate.token(), "Global Tech Giant", "Principal Engineer", "2022-07-01", null, true, "Running platforms", 1);

        // 15. Replace resume A with resume B
        uploadResume(candidate.token(), "resume_historic_B.pdf");

        // 16. Read the original application snapshot directly from database
        CandidateSnapshot snapAfterAllMutations = getApplicationCandidateSnapshot(appId);
        ResumeSnapshot resumeAfterAllMutations = getApplicationResumeSnapshot(appId);

        // 17. Verify the application snapshot is EXACTLY the state from step 8
        assertThat(snapAfterAllMutations).isEqualTo(snapAtStep8);
        assertThat(resumeAfterAllMutations).isEqualTo(resumeAtStep8);

        // Detailed field assertions
        assertThat(snapAfterAllMutations.fullName()).isEqualTo("Historical Candidate");
        assertThat(snapAfterAllMutations.headline()).isEqualTo("Full Stack Intern");
        assertThat(snapAfterAllMutations.location()).isEqualTo("Hyderabad");
        assertThat(snapAfterAllMutations.education()).hasSize(1);
        assertThat(snapAfterAllMutations.education().get(0).institution()).isEqualTo("Historical University");
        assertThat(snapAfterAllMutations.skills()).containsExactlyInAnyOrder("Java", "PostgreSQL");
        assertThat(snapAfterAllMutations.projects()).hasSize(1);
        assertThat(snapAfterAllMutations.projects().get(0).title()).isEqualTo("Historical Project");
        assertThat(snapAfterAllMutations.experience()).hasSize(1);
        assertThat(snapAfterAllMutations.experience().get(0).companyName()).isEqualTo("Historic Labs");

        assertThat(resumeAfterAllMutations.originalFilename()).isEqualTo("resume_historic_A.pdf");
        assertThat(resumeAfterAllMutations.storageKey()).isEqualTo(resumeAtStep8.storageKey());
    }

    @Test
    @DisplayName("P5.4-8: Database JSON persistence test - JSON columns contain valid JSON structure")
    void databaseJsonPersistenceTest() throws Exception {
        UserAuth candidate = registerCandidate("p54.dbjson@example.com", "DB JSON Candidate");
        updateProfile(candidate.token(), "DB JSON Candidate", "Dev", "Bengaluru", "Summary");
        addEducation(candidate.token(), "Institute", "B.E.", "CS", 2019, 2023, 1);
        addSkill(candidate.token(), "Java");
        uploadResume(candidate.token(), "db_test_resume.pdf");

        Long jobId = createActiveJob("dbjson1");
        Long appId = applyToJob(candidate.token(), jobId);

        String rawCandidateSnapshot = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, appId);
        String rawResumeSnapshot = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, appId);

        assertThat(rawCandidateSnapshot).isNotNull().isNotBlank();
        assertThat(rawResumeSnapshot).isNotNull().isNotBlank();

        // Must parse as valid JSON tree
        JsonNode candidateNode = objectMapper.readTree(rawCandidateSnapshot);
        if (candidateNode.isTextual()) {
            candidateNode = objectMapper.readTree(candidateNode.asText());
        }
        assertThat(candidateNode.isObject()).isTrue();
        assertThat(candidateNode.has("candidateId")).isTrue();
        assertThat(candidateNode.has("fullName")).isTrue();
        assertThat(candidateNode.has("education")).isTrue();
        assertThat(candidateNode.has("skills")).isTrue();

        JsonNode resumeNode = objectMapper.readTree(rawResumeSnapshot);
        if (resumeNode.isTextual()) {
            resumeNode = objectMapper.readTree(resumeNode.asText());
        }
        assertThat(resumeNode.isObject()).isTrue();
        assertThat(resumeNode.has("originalFilename")).isTrue();
        assertThat(resumeNode.has("storageKey")).isTrue();
        assertThat(resumeNode.has("contentType")).isTrue();
        assertThat(resumeNode.has("sizeBytes")).isTrue();
    }

    @Test
    @DisplayName("P5.4-9: Deterministic collection ordering in snapshots")
    void deterministicOrderingTest() throws Exception {
        UserAuth candidate = registerCandidate("p54.order@example.com", "Order Candidate");
        updateProfile(candidate.token(), "Order Candidate", "Dev", "Bengaluru", "Summary");

        // Add educations with explicit sortOrders
        addEducation(candidate.token(), "College Two", "M.Tech", "CS", 2022, 2024, 2);
        addEducation(candidate.token(), "College One", "B.Tech", "CS", 2018, 2022, 1);

        // Add skills in unordered sequence
        addSkill(candidate.token(), "Zookeeper");
        addSkill(candidate.token(), "Algorithm");
        addSkill(candidate.token(), "Java");

        uploadResume(candidate.token(), "resume_order.pdf");

        Long jobId = createActiveJob("order1");
        Long appId = applyToJob(candidate.token(), jobId);

        CandidateSnapshot snap = getApplicationCandidateSnapshot(appId);

        // Education should be ordered by sortOrder ASC
        assertThat(snap.education()).hasSize(2);
        assertThat(snap.education().get(0).institution()).isEqualTo("College One");
        assertThat(snap.education().get(1).institution()).isEqualTo("College Two");

        // Skills should be deterministically ordered alphabetically (from repository ORDER BY s.name ASC)
        assertThat(snap.skills()).containsExactly("Algorithm", "Java", "Zookeeper");
    }
}
