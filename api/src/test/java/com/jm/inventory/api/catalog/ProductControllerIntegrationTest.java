package com.jm.inventory.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jm.inventory.domain.user.Role;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import com.jm.inventory.api.support.TruncateBusinessTablesExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * See {@link CategoryControllerIntegrationTest}'s Javadoc for why the JWT is mocked and the
 * Testcontainers Postgres is real - same scope and reasoning here, applied to
 * {@code /api/v1/products}. {@code ProductController} additionally depends on
 * {@code CategoryRepository} for the {@code categoryId} reference check, so each test seeds
 * a category first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class ProductControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "simple");
        // Many test classes share one real local Redis across a single test run under
        // the same mocked JWT subject - without this, their cumulative request count
        // can cross the production default and spuriously trip ApiRateLimitFilter.
        registry.add("app.rate-limit.api-wide.max-requests", () -> 1_000_000);
    }

    @Autowired
    private MockMvc mockMvc;

    private JwtRequestPostProcessor authenticatedAsManager;
    private JwtRequestPostProcessor authenticatedAsStaff;

    @BeforeEach
    void setUpAuthenticatedJwts() {
        // MANAGER - product/category create requires at least MANAGER, per
        // docs/specs/06-roles-and-permissions/requirements.md.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingProducts_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aStaffRoleToken_when_creatingAProduct_then_forbidden() throws Exception {
        String categoryId = createCategory("Electronics");

        mockMvc.perform(post("/api/v1/products").with(authenticatedAsStaff)
                .contentType("application/json")
                .content(productJson("SKU-1", "Widget", categoryId, "EACH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_anUnknownCategoryId_when_creatingAProduct_then_unprocessable() throws Exception {
        String requestBody = "{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"categoryId\":\""
                + UUID.randomUUID() + "\",\"unitOfMeasure\":\"EACH\"}";

        mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                .contentType("application/json")
                .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("categoryId")));
    }

    @Test
    void given_aKnownCategory_when_creatingAProduct_then_createdActiveByDefault() throws Exception {
        String categoryId = createCategory("Electronics");

        mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                .contentType("application/json")
                .content(productJson("SKU-1", "Widget", categoryId, "EACH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("SKU-1")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void given_aProductWithAnExistingSku_when_creatingAnotherWithTheSameSku_then_conflict() throws Exception {
        String categoryId = createCategory("Electronics");
        mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                .contentType("application/json")
                .content(productJson("SKU-1", "Widget", categoryId, "EACH")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                .contentType("application/json")
                .content(productJson("SKU-1", "Another Widget", categoryId, "EACH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_SKU_ALREADY_EXISTS")));
    }

    @Test
    void given_anInvalidUnitOfMeasure_when_creatingAProduct_then_badRequest() throws Exception {
        String categoryId = createCategory("Electronics");

        mockMvc.perform(post("/api/v1/products").with(authenticatedAsManager)
                .contentType("application/json")
                .content(productJson("SKU-1", "Widget", categoryId, "BOXES")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_anActiveProduct_when_deactivatingIt_then_excludedFromTheDefaultList() throws Exception {
        String categoryId = createCategory("Electronics");
        String productId = createProduct("SKU-1", "Widget", categoryId);

        mockMvc.perform(patch("/api/v1/products/" + productId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(get("/api/v1/products").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/products?active=any").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void given_aValidCsv_when_importing_then_allRowsImportedWithNoErrors() throws Exception {
        createCategory("Electronics");
        String csv = "sku,name,description,categoryName,unitOfMeasure\n"
                + "SKU-1,Widget,A widget,Electronics,EACH\n"
                + "SKU-2,Gadget,,Electronics,KG\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/products/import").file(file).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.importedCount", is(2)))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        mockMvc.perform(get("/api/v1/products").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void given_rowsWithBlankSkuDuplicateSkuAndUnknownCategory_when_importing_then_partialSuccessWithRowErrors()
            throws Exception {
        createCategory("Electronics");
        String csv = "sku,name,description,categoryName,unitOfMeasure\n"
                + "SKU-1,Widget,,Electronics,EACH\n"
                + ",Blank Sku,,Electronics,EACH\n"
                + "SKU-1,Duplicate Widget,,Electronics,EACH\n"
                + "SKU-2,Unknown Category,,Nonexistent,EACH\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/products/import").file(file).with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(4)))
                .andExpect(jsonPath("$.importedCount", is(1)))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].row", is(2)))
                .andExpect(jsonPath("$.errors[1].row", is(3)))
                .andExpect(jsonPath("$.errors[1].sku", is("SKU-1")))
                .andExpect(jsonPath("$.errors[2].row", is(4)));
    }

    @Test
    void given_aStaffRoleToken_when_importing_then_forbidden() throws Exception {
        String csv = "sku,name,description,categoryName,unitOfMeasure\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/products/import").file(file).with(authenticatedAsStaff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_moreThanOneThousandDataRows_when_importing_then_unprocessableAndNothingPersisted() throws Exception {
        createCategory("Electronics");
        StringBuilder csv = new StringBuilder("sku,name,description,categoryName,unitOfMeasure\n");
        for (int rowNumber = 1; rowNumber <= 1001; rowNumber++) {
            csv.append("SKU-").append(rowNumber).append(",Widget,,Electronics,EACH\n");
        }
        MockMultipartFile file =
                new MockMultipartFile("file", "products.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/products/import").file(file).with(authenticatedAsManager))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/products?active=any").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void given_existingProducts_when_exporting_then_csvContainsThemInTheImportColumnOrder() throws Exception {
        String categoryId = createCategory("Electronics");
        createProduct("SKU-1", "Widget", categoryId);

        MvcResult result = mockMvc.perform(get("/api/v1/products/export").with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(mvcResult -> assertThat(mvcResult.getResponse().getContentType()).contains("text/csv"))
                .andReturn();

        String csvBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csvBody).startsWith("sku,name,description,categoryName,unitOfMeasure");
        assertThat(csvBody).contains("SKU-1,Widget,,Electronics,EACH");
    }

    @Test
    void given_noAuthentication_when_exporting_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products/export")).andExpect(status().isUnauthorized());
    }

    private String createCategory(String name) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return idFromLocation(result);
    }

    private String createProduct(String sku, String name, String categoryId) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/products").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(productJson(sku, name, categoryId, "EACH")))
                .andReturn();
        return idFromLocation(result);
    }

    private String productJson(String sku, String name, String categoryId, String unitOfMeasure) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"categoryId\":\"" + categoryId
                + "\",\"unitOfMeasure\":\"" + unitOfMeasure + "\"}";
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
