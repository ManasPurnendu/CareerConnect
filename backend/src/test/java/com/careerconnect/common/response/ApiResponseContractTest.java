package com.careerconnect.common.response;

import com.careerconnect.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Single item response must match canonical success envelope")
    void shouldSerializeSingleItemResponse() throws Exception {
        Map<String, Object> payload = Map.of("id", 1, "title", "Software Engineer Intern");
        ApiResponse<Map<String, Object>> response = ApiResponse.success(payload);

        String json = objectMapper.writeValueAsString(response);
        var jsonNode = objectMapper.readTree(json);

        assertThat(jsonNode.get("success").asBoolean()).isTrue();
        assertThat(jsonNode.get("data").get("id").asInt()).isEqualTo(1);
        assertThat(jsonNode.get("data").get("title").asText()).isEqualTo("Software Engineer Intern");
        assertThat(jsonNode.has("pagination")).isFalse();
    }

    @Test
    @DisplayName("Collection response must include pagination metadata matching specification")
    void shouldSerializeCollectionResponseWithPagination() throws Exception {
        List<String> items = List.of("Java", "Spring Boot", "MySQL");
        PaginationMetadata pagination = PaginationMetadata.of(0, 20, 142);
        ApiResponse<List<String>> response = ApiResponse.success(items, pagination);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"page\":0");
        assertThat(json).contains("\"size\":20");
        assertThat(json).contains("\"totalElements\":142");
        assertThat(json).contains("\"totalPages\":8");
    }

    @Test
    @DisplayName("Error response must include success=false, data=null, message, and errorCode")
    void shouldSerializeErrorResponse() throws Exception {
        ApiErrorResponse error = ApiErrorResponse.of(
                "Application deadline has passed.",
                ErrorCode.JOB_DEADLINE_PASSED.getCode()
        );

        String json = objectMapper.writeValueAsString(error);

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"data\":null");
        assertThat(json).contains("\"message\":\"Application deadline has passed.\"");
        assertThat(json).contains("\"errorCode\":\"JOB_DEADLINE_PASSED\"");
    }
}
