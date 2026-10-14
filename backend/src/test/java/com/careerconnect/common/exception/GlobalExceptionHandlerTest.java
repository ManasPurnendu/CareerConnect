package com.careerconnect.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.security.test.context.support.WithMockUser
@org.springframework.context.annotation.Import(GlobalExceptionHandlerTest.TestExceptionController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BusinessRuleException must map to HTTP 422 with specified errorCode and data:null")
    void shouldMapBusinessRuleExceptionTo422() throws Exception {
        mockMvc.perform(get("/api/test-exceptions/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_DEADLINE_PASSED"))
                .andExpect(jsonPath("$.message").value("Application deadline has passed."));
    }

    @Test
    @DisplayName("ResourceNotFoundException must map to HTTP 404 with RESOURCE_NOT_FOUND and data:null")
    void shouldMapResourceNotFoundExceptionTo404() throws Exception {
        mockMvc.perform(get("/api/test-exceptions/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Job with ID 999 not found."));
    }

    @Test
    @DisplayName("ConflictException must map to HTTP 409 with DUPLICATE_APPLICATION and data:null")
    void shouldMapConflictExceptionTo409() throws Exception {
        mockMvc.perform(get("/api/test-exceptions/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"))
                .andExpect(jsonPath("$.message").value("You have already applied to this job."));
    }

    @Test
    @DisplayName("Bean Validation failure must map to HTTP 400 with VALIDATION_FAILED and data:null")
    void shouldMapBeanValidationTo400() throws Exception {
        mockMvc.perform(post("/api/test-exceptions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @RestController
    @RequestMapping("/api/test-exceptions")
    static class TestExceptionController {

        @GetMapping("/business-rule")
        public void throwBusinessRule() {
            throw new BusinessRuleException("Application deadline has passed.", ErrorCode.JOB_DEADLINE_PASSED);
        }

        @GetMapping("/not-found")
        public void throwNotFound() {
            throw new ResourceNotFoundException("Job with ID 999 not found.");
        }

        @GetMapping("/conflict")
        public void throwConflict() {
            throw new ConflictException("You have already applied to this job.", ErrorCode.DUPLICATE_APPLICATION);
        }

        @PostMapping("/validate")
        public void validate(@Valid @RequestBody TestValidationDto dto) {
        }
    }

    static class TestValidationDto {
        @NotBlank(message = "Name must not be blank")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
