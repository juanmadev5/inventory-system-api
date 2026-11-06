package com.jm.inventory.api.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import com.jm.inventory.domain.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Every error response carries a stable {@code code}, per docs/conventions/api-design.md -
 * including the ones Spring itself raises before or around a controller (Bean Validation,
 * malformed body, unknown route, wrong method, missing token), which
 * {@code GlobalExceptionHandler}'s own handlers never see.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class ErrorResponsesIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "simple");
        // See WarehouseControllerIntegrationTest - shared local Redis, shared mocked subject.
        registry.add("app.rate-limit.api-wide.max-requests", () -> 1_000_000);
    }

    @Autowired
    private MockMvc mockMvc;

    private JwtRequestPostProcessor authenticatedAsManager;

    @BeforeEach
    void setUpAuthenticatedJwt() {
        authenticatedAsManager = jwt().authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
    }

    @Test
    void given_aBodyFailingBeanValidation_when_posted_then_badRequestWithValidationFailedAndFieldErrors()
            throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
    }

    @Test
    void given_aNonPositiveQuantity_when_recordingAMovement_then_validationFailedOnQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"warehouseId\":\"" + UUID.randomUUID()
                                + "\",\"type\":\"RECEIPT\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("quantity")));
    }

    @Test
    void given_malformedJson_when_posted_then_badRequestWithMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")));
    }

    @Test
    void given_anUnknownEnumValue_when_posted_then_badRequestWithMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"warehouseId\":\"" + UUID.randomUUID()
                                + "\",\"type\":\"TELEPORT\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")));
    }

    @Test
    void given_aPathIdThatIsNotAUuid_when_requested_then_badRequestWithMalformedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/products/not-a-uuid").with(authenticatedAsManager))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")));
    }

    @Test
    void given_anUnknownRoute_when_requested_then_notFoundWithEndpointNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint").with(authenticatedAsManager))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ENDPOINT_NOT_FOUND")));
    }

    @Test
    void given_anUnsupportedMethod_when_requested_then_methodNotAllowedWithCode() throws Exception {
        mockMvc.perform(delete("/api/v1/categories").with(authenticatedAsManager))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("METHOD_NOT_ALLOWED")));
    }

    @Test
    void given_anUnsupportedContentType_when_posted_then_unsupportedMediaTypeWithCode() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("text/plain")
                        .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }

    @Test
    void given_noAccessToken_when_requested_then_unauthorizedWithProblemDetailAndBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    void given_anInvalidAccessToken_when_requested_then_unauthorizedWithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    void given_anUnknownReference_when_posted_then_unprocessableWithValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Child\",\"parentCategoryId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("parentCategoryId")));
    }
}
