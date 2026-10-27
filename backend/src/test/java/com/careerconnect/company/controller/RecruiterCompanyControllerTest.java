package com.careerconnect.company.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.company.dto.RecruiterCompanyUpsertRequest;
import com.careerconnect.company.service.RecruiterCompanyService;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterCompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RecruiterCompanyService recruiterCompanyService;

    private record RecruiterAuth(String token, Long recruiterId, Long userId) {}

    private RecruiterAuth registerRecruiter(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter Test");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long recruiterId = root.path("data").path("user").path("recruiterId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new RecruiterAuth(token, recruiterId, userId);
    }

    private String registerCandidate(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, "Candidate Test");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private String generateAdminToken() {
        SecurityUser adminUser = new SecurityUser(
                1L, "admin@example.com", "hash", Role.ADMIN, "System Admin",
                null, null, null, true
        );
        return jwtService.generateToken(adminUser);
    }

    // ==========================================
    // A. Authentication Tests
    // ==========================================

    @Test
    @DisplayName("A1: Unauthenticated GET /api/recruiter/company returns 401 UNAUTHENTICATED")
    void unauthenticatedGetReturns401() throws Exception {
        mockMvc.perform(get("/api/recruiter/company"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("A2: Unauthenticated PUT /api/recruiter/company returns 401 UNAUTHENTICATED")
    void unauthenticatedPutReturns401() throws Exception {
        RecruiterCompanyUpsertRequest req = new RecruiterCompanyUpsertRequest(
                "Acme Corp", null, "A tech company", "Tech", "https://acme.com", "Pune", "51-200"
        );

        mockMvc.perform(put("/api/recruiter/company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHENTICATED")));
    }

    // ==========================================
    // B. Authorization Tests
    // ==========================================

    @Test
    @DisplayName("B3: Candidate GET /api/recruiter/company returns 403 FORBIDDEN")
    void candidateGetReturns403() throws Exception {
        String candidateToken = registerCandidate("cand.auth.get");

        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("B4: Candidate PUT /api/recruiter/company returns 403 FORBIDDEN")
    void candidatePutReturns403() throws Exception {
        String candidateToken = registerCandidate("cand.auth.put");
        RecruiterCompanyUpsertRequest req = new RecruiterCompanyUpsertRequest(
                "Acme Corp", null, "A tech company", "Tech", "https://acme.com", "Pune", "51-200"
        );

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("B5: Admin GET /api/recruiter/company returns 403 FORBIDDEN")
    void adminGetReturns403() throws Exception {
        String adminToken = generateAdminToken();

        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("B6: Admin PUT /api/recruiter/company returns 403 FORBIDDEN")
    void adminPutReturns403() throws Exception {
        String adminToken = generateAdminToken();
        RecruiterCompanyUpsertRequest req = new RecruiterCompanyUpsertRequest(
                "Acme Corp", null, "A tech company", "Tech", "https://acme.com", "Pune", "51-200"
        );

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    // ==========================================
    // C. GET Behavior Tests
    // ==========================================

    @Test
    @DisplayName("C9: Recruiter with no company returns 404 RESOURCE_NOT_FOUND")
    void recruiterWithNoCompanyReturns404() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.no.comp");

        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("C10 & C11: Recruiter with company returns 200 and their own company data")
    void recruiterWithCompanyReturns200() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.with.comp");

        // Create company first
        String companyName = "Apex Innovations " + UUID.randomUUID();
        RecruiterCompanyUpsertRequest createReq = new RecruiterCompanyUpsertRequest(
                companyName, null, "Apex description", "Fintech", "https://apex.example.com", "Mumbai", "201-500"
        );

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk());

        // GET company
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.name", is(companyName)))
                .andExpect(jsonPath("$.data.description", is("Apex description")))
                .andExpect(jsonPath("$.data.industry", is("Fintech")))
                .andExpect(jsonPath("$.data.website", is("https://apex.example.com")))
                .andExpect(jsonPath("$.data.location", is("Mumbai")))
                .andExpect(jsonPath("$.data.companySize", is("201-500")))
                .andExpect(jsonPath("$.data.isActive", is(true)))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()))
                .andExpect(jsonPath("$.data.updatedAt", notNullValue()));
    }

    // ==========================================
    // D. CREATE Behavior Tests
    // ==========================================

    @Test
    @DisplayName("D12-15: Recruiter with NULL company_id creates company and links recruiter.company_id")
    void recruiterCreatesCompanySuccessfully() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.create.comp");

        // Verify initial state: company_id is NULL
        Long initialCompanyId = jdbcTemplate.queryForObject(
                "SELECT company_id FROM recruiters WHERE id = ?", Long.class, recruiter.recruiterId());
        assertThat(initialCompanyId).isNull();

        String uniqueName = "Nimbus Technologies " + UUID.randomUUID();
        RecruiterCompanyUpsertRequest createReq = new RecruiterCompanyUpsertRequest(
                uniqueName, "https://logo.example.com/nimbus.png", "Cloud platforms", "Cloud", "https://nimbus.example.com", "Hyderabad", "501-1000"
        );

        MvcResult res = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is(uniqueName)))
                .andExpect(jsonPath("$.data.industry", is("Cloud")))
                .andExpect(jsonPath("$.data.location", is("Hyderabad")))
                .andExpect(jsonPath("$.data.companySize", is("501-1000")))
                .andExpect(jsonPath("$.data.isActive", is(true)))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        Long createdCompanyId = root.path("data").path("id").asLong();

        // Verify database: company row exists
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE id = ? AND name = ?", Integer.class, createdCompanyId, uniqueName);
        assertThat(count).isEqualTo(1);

        // Verify database: recruiter.company_id is updated to createdCompanyId
        Long updatedCompanyId = jdbcTemplate.queryForObject(
                "SELECT company_id FROM recruiters WHERE id = ?", Long.class, recruiter.recruiterId());
        assertThat(updatedCompanyId).isEqualTo(createdCompanyId);
    }

    @Test
    @DisplayName("D16: Transaction rollback - no orphan company remains if association fails")
    void transactionRollsBackOnRecruiterAssociationFailure() {
        String orphanTestName = "Orphan Rollback Test Corp " + UUID.randomUUID();
        RecruiterCompanyUpsertRequest req = new RecruiterCompanyUpsertRequest(
                orphanTestName, null, "Will rollback", "IT", "https://rollback.com", "Delhi", "1-10"
        );

        // Calling service with non-existent recruiter ID
        Long nonExistentRecruiterId = 99999999L;
        assertThatThrownBy(() -> recruiterCompanyService.upsertCompany(nonExistentRecruiterId, req))
                .isInstanceOf(Exception.class);

        // Verify no orphan company was inserted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE name = ?", Integer.class, orphanTestName);
        assertThat(count).isEqualTo(0);
    }

    // ==========================================
    // E. UPDATE Behavior Tests
    // ==========================================

    @Test
    @DisplayName("E17-20: Recruiter with existing company updates it without creating a new company")
    void recruiterUpdatesExistingCompany() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.update.comp");

        // 1. Create company
        String originalName = "Original Corp " + UUID.randomUUID();
        RecruiterCompanyUpsertRequest createReq = new RecruiterCompanyUpsertRequest(
                originalName, null, "Original desc", "Manufacturing", "https://orig.com", "Chennai", "51-200"
        );

        MvcResult createRes = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        Long companyId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        int companyCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies", Integer.class);

        // 2. Update company
        String updatedName = "Updated Corp " + UUID.randomUUID();
        RecruiterCompanyUpsertRequest updateReq = new RecruiterCompanyUpsertRequest(
                updatedName, null, "Updated desc", "Advanced Tech", "https://updated.com", "Bengaluru", "501-1000"
        );

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(companyId.intValue())))
                .andExpect(jsonPath("$.data.name", is(updatedName)))
                .andExpect(jsonPath("$.data.description", is("Updated desc")))
                .andExpect(jsonPath("$.data.industry", is("Advanced Tech")))
                .andExpect(jsonPath("$.data.website", is("https://updated.com")))
                .andExpect(jsonPath("$.data.location", is("Bengaluru")))
                .andExpect(jsonPath("$.data.companySize", is("501-1000")));

        // 3. Verify company count did NOT increase
        int companyCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies", Integer.class);
        assertThat(companyCountAfter).isEqualTo(companyCountBefore);

        // 4. Verify database persistence
        String dbName = jdbcTemplate.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, companyId);
        assertThat(dbName).isEqualTo(updatedName);
    }

    // ==========================================
    // F. Ownership & Spoofing Prevention
    // ==========================================

    @Test
    @DisplayName("F21: Recruiter A cannot modify Recruiter B's company")
    void recruiterCannotModifyOtherCompany() throws Exception {
        RecruiterAuth recruiterA = registerRecruiter("rec.owner.a");
        RecruiterAuth recruiterB = registerRecruiter("rec.owner.b");

        // Recruiter A creates Company A
        String companyAName = "Company Alpha " + UUID.randomUUID();
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiterA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                companyAName, null, "A desc", "Auto", "https://a.com", "Pune", "11-50"
                        ))))
                .andExpect(status().isOk());

        // Recruiter B creates Company B
        String companyBName = "Company Beta " + UUID.randomUUID();
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiterB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                companyBName, null, "B desc", "Finance", "https://b.com", "Delhi", "11-50"
                        ))))
                .andExpect(status().isOk());

        // Recruiter B tries to update their own company — Recruiter A's company must NOT be affected
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiterB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                "Company Beta Renamed " + UUID.randomUUID(), null, "B modified", "Finance", "https://b.com", "Delhi", "11-50"
                        ))))
                .andExpect(status().isOk());

        // Confirm Company A remains unmodified in database
        String currentCompanyAName = jdbcTemplate.queryForObject(
                "SELECT name FROM companies WHERE name = ?", String.class, companyAName);
        assertThat(currentCompanyAName).isEqualTo(companyAName);
    }

    @Test
    @DisplayName("F22 & F23: Client-supplied companyId or recruiterId in payload is ignored")
    void clientSuppliedIdsAreIgnored() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.spoof.test");

        // Inject foreign companyId: 99999 and foreign recruiterId: 88888 into raw JSON payload
        String spoofJson = """
                {
                  "companyId": 99999,
                  "recruiterId": 88888,
                  "userId": 77777,
                  "name": "Spoof Defense Corp %s",
                  "description": "Safe payload",
                  "industry": "Cybersecurity",
                  "website": "https://spoof-defense.com",
                  "location": "Bengaluru",
                  "companySize": "11-50"
                }
                """.formatted(UUID.randomUUID());

        MvcResult res = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        Long actualCompanyId = root.path("data").path("id").asLong();

        // Must NOT equal 99999
        assertThat(actualCompanyId).isNotEqualTo(99999L);

        // Recruiter in DB must point to actualCompanyId, NOT 99999
        Long linkedCompanyId = jdbcTemplate.queryForObject(
                "SELECT company_id FROM recruiters WHERE id = ?", Long.class, recruiter.recruiterId());
        assertThat(linkedCompanyId).isEqualTo(actualCompanyId);
    }

    // ==========================================
    // G. Cardinality & Conflict Tests
    // ==========================================

    @Test
    @DisplayName("G24 & G25: Repeated PUT updates rather than creates another company")
    void repeatedPutMaintainsSingleCompanyPerRecruiter() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.cardinality");

        // First PUT -> Creates company
        String name1 = "Cardinality Corp 1 " + UUID.randomUUID();
        MvcResult res1 = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                name1, null, "Desc 1", "IT", "https://c1.com", "Pune", "1-10"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        Long companyId1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Second PUT -> Updates company
        String name2 = "Cardinality Corp 2 " + UUID.randomUUID();
        MvcResult res2 = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                name2, null, "Desc 2", "IT", "https://c2.com", "Pune", "1-10"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        Long companyId2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Company ID must remain identical
        assertThat(companyId2).isEqualTo(companyId1);

        // Recruiter in DB still points to that single company
        Long currentCompanyId = jdbcTemplate.queryForObject(
                "SELECT company_id FROM recruiters WHERE id = ?", Long.class, recruiter.recruiterId());
        assertThat(currentCompanyId).isEqualTo(companyId1);
    }

    @Test
    @DisplayName("G26: Duplicate company name returns 409 COMPANY_ALREADY_EXISTS")
    void duplicateCompanyNameReturns409() throws Exception {
        RecruiterAuth recruiterA = registerRecruiter("rec.dup.a");
        RecruiterAuth recruiterB = registerRecruiter("rec.dup.b");

        String sharedName = "Shared Unique Name " + UUID.randomUUID();

        // Recruiter A creates company with sharedName
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiterA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                sharedName, null, "Desc A", "IT", "https://a.com", "Pune", "1-10"
                        ))))
                .andExpect(status().isOk());

        // Recruiter B tries to create company with same sharedName -> 409
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiterB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                sharedName, null, "Desc B", "IT", "https://b.com", "Mumbai", "1-10"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("COMPANY_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("G27: Blank company name returns 400 VALIDATION_FAILED")
    void blankCompanyNameReturns400() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.blank.name");

        RecruiterCompanyUpsertRequest req = new RecruiterCompanyUpsertRequest(
                "   ", null, "Desc", "IT", "https://blank.com", "Pune", "1-10"
        );

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")));
    }

    // ==========================================
    // H. Regression Tests
    // ==========================================

    @Test
    @DisplayName("H28: Public GET /api/companies and GET /api/companies/{id} remain fully functional")
    void publicCompanyEndpointsRemainFunctional() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("rec.regress.pub");

        String companyName = "Public Discovery Corp " + UUID.randomUUID();
        MvcResult res = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecruiterCompanyUpsertRequest(
                                companyName, null, "Public company desc", "Fintech", "https://pub.com", "Bengaluru", "100+"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        Long companyId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Public list
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));

        // Public detail
        mockMvc.perform(get("/api/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(companyId.intValue())))
                .andExpect(jsonPath("$.data.name", is(companyName)));
    }
}
