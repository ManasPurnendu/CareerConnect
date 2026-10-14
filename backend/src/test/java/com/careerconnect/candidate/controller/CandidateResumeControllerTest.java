package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.repository.CandidateResumeRepository;
import com.careerconnect.candidate.service.CandidateResumeService;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CandidateResumeRepository candidateResumeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    @AfterEach
    void tearDown() throws Exception {
        jdbcTemplate.execute("DELETE FROM resumes");
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            }
        }
    }

    private record UserAuth(String token, Long candidateId) {}

    private UserAuth registerUserAndGetAuth(String email, Role role, String fullName) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", role, fullName);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        return new UserAuth(token, candidateId);
    }

    private MockMultipartFile createValidPdf(String filename, String content) {
        byte[] pdfBytes = ("%PDF-1.4\n" + content).getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile("file", filename, "application/pdf", pdfBytes);
    }

    @Test
    @DisplayName("P4.7-1 & P4.7-2: Successful PDF upload -> 201 and persists metadata")
    void shouldUploadPdfSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.upload@example.com", Role.CANDIDATE, "Alice Dev");
        MockMultipartFile file = createValidPdf("alice_resume.pdf", "Candidate resume details");

        MvcResult result = mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.originalFilename").value("alice_resume.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.sizeBytes").value(file.getSize()))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long resumeId = root.path("data").path("id").asLong();

        // Check persistence in database
        var currentOpt = candidateResumeRepository.findByIdAndCandidateId(resumeId, user.candidateId());
        assertThat(currentOpt).isPresent();
        assertThat(currentOpt.get().originalFilename()).isEqualTo("alice_resume.pdf");
        assertThat(currentOpt.get().isCurrent()).isTrue();
    }

    @Test
    @DisplayName("P4.7-3: Current resume is retrievable via GET /api/candidate/resume")
    void shouldRetrieveCurrentResume() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.retrievable@example.com", Role.CANDIDATE, "Bob Dev");
        MockMultipartFile file = createValidPdf("bob_resume.pdf", "Bob resume text");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("bob_resume.pdf"))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    @DisplayName("P4.7-4, 5, 6: Replace existing resume successfully, new becomes current, old becomes non-current")
    void shouldReplaceExistingResumeSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.replace@example.com", Role.CANDIDATE, "Carol Dev");

        MockMultipartFile firstFile = createValidPdf("v1_resume.pdf", "First resume version");
        MvcResult firstResult = mockMvc.perform(multipart("/api/candidate/resume")
                        .file(firstFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated())
                .andReturn();

        Long firstId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        MockMultipartFile secondFile = createValidPdf("v2_resume.pdf", "Second resume version");
        MvcResult secondResult = mockMvc.perform(multipart("/api/candidate/resume")
                        .file(secondFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFilename").value("v2_resume.pdf"))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andReturn();

        Long secondId = objectMapper.readTree(secondResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        assertThat(secondId).isNotEqualTo(firstId);

        // Verify old resume is no longer current
        var oldResume = candidateResumeRepository.findByIdAndCandidateId(firstId, user.candidateId());
        assertThat(oldResume).isPresent();
        assertThat(oldResume.get().isCurrent()).isFalse();

        // Verify new resume is current
        var newResume = candidateResumeRepository.findByIdAndCandidateId(secondId, user.candidateId());
        assertThat(newResume).isPresent();
        assertThat(newResume.get().isCurrent()).isTrue();

        // Verify GET returns the new resume
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(secondId))
                .andExpect(jsonPath("$.data.originalFilename").value("v2_resume.pdf"));
    }

    @Test
    @DisplayName("P4.7-7 & P4.7-8: Delete current resume -> 204, and GET after deletion -> 404")
    void shouldDeleteCurrentResumeAndReturn404OnSubsequentGet() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.delete@example.com", Role.CANDIDATE, "Dave Dev");
        MockMultipartFile file = createValidPdf("dave_resume.pdf", "Dave resume");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.7-9: Upload non-PDF MIME type -> 400")
    void shouldRejectNonPdfMimeType() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.mimetype@example.com", Role.CANDIDATE, "Eve Dev");
        MockMultipartFile textFile = new MockMultipartFile("file", "resume.txt", "text/plain", "%PDF-1.4 text content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(textFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESUME_INVALID_TYPE"));
    }

    @Test
    @DisplayName("P4.7-10: Upload fake PDF with application/pdf but invalid magic bytes -> 400")
    void shouldRejectFakePdfWithInvalidMagicBytes() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.magicbytes@example.com", Role.CANDIDATE, "Frank Dev");
        MockMultipartFile fakeFile = new MockMultipartFile("file", "fake.pdf", "application/pdf", "Not a real PDF file".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESUME_INVALID_TYPE"));
    }

    @Test
    @DisplayName("P4.7-11: Upload file without application/pdf (null or different) -> 400")
    void shouldRejectFileWithoutApplicationPdf() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.nopdf@example.com", Role.CANDIDATE, "Grace Dev");
        MockMultipartFile imageFile = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(imageFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESUME_INVALID_TYPE"));
    }

    @Test
    @DisplayName("P4.7-12: Upload file > 5 MiB -> 400")
    void shouldRejectFileLargerThan5MiB() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.large@example.com", Role.CANDIDATE, "Hank Dev");
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1024];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.UTF_8), 0, largeBytes, 0, 5);
        MockMultipartFile largeFile = new MockMultipartFile("file", "oversized.pdf", "application/pdf", largeBytes);

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESUME_TOO_LARGE"));
    }

    @Test
    @DisplayName("P4.7-13: Missing file -> 400")
    void shouldRejectMissingFile() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.nofile@example.com", Role.CANDIDATE, "Ivy Dev");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("P4.7-14: Unauthenticated upload -> 401")
    void shouldRejectUnauthenticatedUpload() throws Exception {
        MockMultipartFile file = createValidPdf("unauth.pdf", "Unauth content");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.7-15: Recruiter attempting upload -> 403")
    void shouldRejectRecruiterUpload() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.resume@example.com", Role.RECRUITER, "Recruiter User");
        MockMultipartFile file = createValidPdf("recruiter.pdf", "Recruiter content");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.7-16: Candidate cannot access another candidate's resume")
    void candidateCannotAccessAnotherCandidateResume() throws Exception {
        UserAuth candidateA = registerUserAndGetAuth("candA.resume@example.com", Role.CANDIDATE, "Candidate A");
        UserAuth candidateB = registerUserAndGetAuth("candB.resume@example.com", Role.CANDIDATE, "Candidate B");

        MockMultipartFile fileA = createValidPdf("candA_resume.pdf", "Candidate A Resume");
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(fileA)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isCreated());

        // Candidate B has no resume, should get 404
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candidateB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Candidate B deletes their own resume -> 404, does not affect Candidate A
        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candidateB.token()))
                .andExpect(status().isNotFound());

        // Candidate A's resume is still intact
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("candA_resume.pdf"));
    }

    @Test
    @DisplayName("P4.7-17: Nonexistent resume -> 404 where applicable")
    void shouldReturn404WhenNoResumeExists() throws Exception {
        UserAuth user = registerUserAndGetAuth("noresume.user@example.com", Role.CANDIDATE, "No Resume User");

        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.7-18: Verify filesystem file exists after successful upload")
    void verifyFilesystemFileExistsAfterUpload() throws Exception {
        UserAuth user = registerUserAndGetAuth("fs.upload@example.com", Role.CANDIDATE, "FS Dev");
        MockMultipartFile file = createValidPdf("fs_test.pdf", "FS upload content");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM resumes WHERE candidate_id = ? AND is_current = TRUE",
                String.class,
                user.candidateId()
        );
        assertThat(storageKey).isNotNull();

        Path path = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(storageKey);
        assertThat(Files.exists(path)).isTrue();
        assertThat(Files.size(path)).isEqualTo(file.getSize());
    }

    @Test
    @DisplayName("P4.7-19: Verify filesystem file is removed after deletion")
    void verifyFilesystemFileRemovedAfterDeletion() throws Exception {
        UserAuth user = registerUserAndGetAuth("fs.delete@example.com", Role.CANDIDATE, "FS Delete Dev");
        MockMultipartFile file = createValidPdf("fs_delete.pdf", "FS delete content");

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM resumes WHERE candidate_id = ? AND is_current = TRUE",
                String.class,
                user.candidateId()
        );
        Path path = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(storageKey);
        assertThat(Files.exists(path)).isTrue();

        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(path)).isFalse();
    }

    @Test
    @DisplayName("P4.7-20: Verify replacement cleans up the previous physical file")
    void verifyReplacementCleansUpPreviousPhysicalFile() throws Exception {
        UserAuth user = registerUserAndGetAuth("fs.replace@example.com", Role.CANDIDATE, "FS Replace Dev");

        MockMultipartFile file1 = createValidPdf("v1.pdf", "Version 1 Content");
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file1)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());

        String key1 = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM resumes WHERE candidate_id = ? AND is_current = TRUE",
                String.class,
                user.candidateId()
        );
        Path path1 = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(key1);
        assertThat(Files.exists(path1)).isTrue();

        MockMultipartFile file2 = createValidPdf("v2.pdf", "Version 2 Content");
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file2)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());

        String key2 = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM resumes WHERE candidate_id = ? AND is_current = TRUE",
                String.class,
                user.candidateId()
        );
        Path path2 = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(key2);

        // Previous file is removed, new file exists
        assertThat(Files.exists(path1)).isFalse();
        assertThat(Files.exists(path2)).isTrue();
    }

    @Test
    @DisplayName("Failure cleanup: If DB persistence fails after filesystem write, file must not remain orphaned")
    void shouldCleanUpFileIfDbPersistenceFails() throws Exception {
        CandidateResumeRepository failingRepo = new CandidateResumeRepository(jdbcTemplate) {
            @Override
            public Long insert(Long candidateId, String originalFilename, String storageKey, String contentType, long sizeBytes) {
                throw new RuntimeException("Simulated database failure");
            }
        };

        CandidateResumeService service = new CandidateResumeService(failingRepo, uploadDir);
        MockMultipartFile file = createValidPdf("fail.pdf", "DB fail test content");

        assertThrows(RuntimeException.class, () -> service.uploadResume(9999L, file));

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                assertThat(stream.count()).isZero();
            }
        }
    }

    @Test
    @DisplayName("Empty file upload -> 400 with CANDIDATE_RESUME_REQUIRED")
    void shouldRejectEmptyFile() throws Exception {
        UserAuth user = registerUserAndGetAuth("empty.file@example.com", Role.CANDIDATE, "Empty Dev");
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANDIDATE_RESUME_REQUIRED"));
    }

    @Test
    @DisplayName("Unauthenticated GET & DELETE -> 401")
    void shouldRejectUnauthenticatedGetAndDelete() throws Exception {
        mockMvc.perform(get("/api/candidate/resume"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(delete("/api/candidate/resume"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Recruiter GET & DELETE -> 403")
    void shouldRejectRecruiterGetAndDelete() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.ops@example.com", Role.RECRUITER, "Recruiter User");

        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
