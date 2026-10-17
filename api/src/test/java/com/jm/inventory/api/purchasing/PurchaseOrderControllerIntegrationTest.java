package com.jm.inventory.api.purchasing;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jm.inventory.domain.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * See {@link com.jm.inventory.api.catalog.CategoryControllerIntegrationTest}'s Javadoc for
 * why the JWT is mocked and the Testcontainers Postgres is real. Also proves the
 * cross-feature reuse this feature is built on: receiving a line calls straight into
 * {@code 02-stock-movements}' recording service (same transaction, same pessimistic lock) -
 * see {@code given_fullyReceivingAnOrder_then_stockLevelReflectsTheReceipt}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(TruncateBusinessTablesExtension.class)
class PurchaseOrderControllerIntegrationTest {

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
        // MANAGER - creating/placing/cancelling/editing a purchase order requires at least
        // MANAGER; receiving a line only requires STAFF - see
        // docs/specs/06-roles-and-permissions/requirements.md.
        authenticatedAsManager = jwt()
                .authorities(new SimpleGrantedAuthority(Role.MANAGER.authorityName()));
        authenticatedAsStaff = jwt()
                .authorities(new SimpleGrantedAuthority(Role.STAFF.authorityName()));
    }

    @Test
    void given_noAuthentication_when_listingPurchaseOrders_then_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void given_aStaffRoleToken_when_creatingAPurchaseOrder_then_forbidden() throws Exception {
        String supplierId = createSupplier();
        String warehouseId = createWarehouse();
        String productId = createProduct();

        mockMvc.perform(post("/api/v1/purchase-orders").with(authenticatedAsStaff)
                .contentType("application/json")
                .content(purchaseOrderJson(supplierId, warehouseId, productId, 10, "5.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    void given_aStaffRoleToken_when_receivingALineOfAnOrderedOrder_then_succeeds() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + fixture.lineId
                        + "/receipts").with(authenticatedAsStaff)
                .contentType("application/json")
                .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantityReceived", is(4)));
    }

    @Test
    void given_anUnknownSupplierId_when_creatingAPurchaseOrder_then_unprocessable() throws Exception {
        String warehouseId = createWarehouse();
        String productId = createProduct();

        mockMvc.perform(post("/api/v1/purchase-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content(purchaseOrderJson(UUID.randomUUID().toString(), warehouseId, productId, 10, "5.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("supplierId")));
    }

    @Test
    void given_anUnknownProductIdInALine_when_creatingAPurchaseOrder_then_unprocessable() throws Exception {
        String supplierId = createSupplier();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/purchase-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content(purchaseOrderJson(supplierId, warehouseId, UUID.randomUUID().toString(), 10, "5.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("productId")));
    }

    @Test
    void given_noLines_when_creatingAPurchaseOrder_then_badRequest() throws Exception {
        String supplierId = createSupplier();
        String warehouseId = createWarehouse();

        mockMvc.perform(post("/api/v1/purchase-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"supplierId\":\"" + supplierId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"lines\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_aValidRequest_when_creatingAPurchaseOrder_then_createdInDraftStatus() throws Exception {
        String supplierId = createSupplier();
        String warehouseId = createWarehouse();
        String productId = createProduct();

        mockMvc.perform(post("/api/v1/purchase-orders").with(authenticatedAsManager)
                .contentType("application/json")
                .content(purchaseOrderJson(supplierId, warehouseId, productId, 10, "5.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.lines[0].quantityOrdered", is(10)))
                .andExpect(jsonPath("$.lines[0].quantityReceived", is(0)));
    }

    @Test
    void given_aDraftOrder_when_placingIt_then_ordered() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/placement")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ORDERED")));
    }

    @Test
    void given_anOrderedOrder_when_placingItAgain_then_conflict() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/placement")
                        .with(authenticatedAsManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PURCHASE_ORDER_NOT_DRAFT")));
    }

    @Test
    void given_aDraftOrder_when_patchingExpectedDeliveryDate_then_updated() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);

        mockMvc.perform(patch("/api/v1/purchase-orders/" + fixture.purchaseOrderId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"expectedDeliveryDate\":\"2026-12-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedDeliveryDate", is("2026-12-01")));
    }

    @Test
    void given_anOrderedOrder_when_patchingExpectedDeliveryDate_then_conflict() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(patch("/api/v1/purchase-orders/" + fixture.purchaseOrderId).with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"expectedDeliveryDate\":\"2026-12-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PURCHASE_ORDER_NOT_EDITABLE")));
    }

    @Test
    void given_aDraftOrder_when_receivingALine_then_conflict() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + fixture.lineId
                        + "/receipts").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PURCHASE_ORDER_NOT_ORDERED")));
    }

    @Test
    void given_anOrderedOrder_when_receivingPartialQuantity_then_staysOrderedAndStockIncreases() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + fixture.lineId
                        + "/receipts").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ORDERED")))
                .andExpect(jsonPath("$.lines[0].quantityReceived", is(4)));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(4)));
    }

    @Test
    void given_fullyReceivingAnOrder_then_autoTransitionsToReceivedAndStockLevelReflectsIt() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + fixture.lineId
                        + "/receipts").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECEIVED")))
                .andExpect(jsonPath("$.lines[0].quantityReceived", is(10)));

        mockMvc.perform(get("/api/v1/stock-levels?productId=" + fixture.productId + "&warehouseId="
                        + fixture.warehouseId).with(authenticatedAsManager))
                .andExpect(jsonPath("$.items[0].quantityOnHand", is(10)));
    }

    @Test
    void given_aReceiptExceedingOrderedQuantity_when_received_then_conflict() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + fixture.lineId
                        + "/receipts").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":11}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("RECEIPT_EXCEEDS_ORDERED_QUANTITY")));
    }

    @Test
    void given_anUnknownLineId_when_receiving_then_notFound() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + UUID.randomUUID()
                        + "/receipts").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_aDraftOrder_when_cancelling_then_cancelled() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/cancellation")
                        .with(authenticatedAsManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void given_aReceivedOrder_when_cancelling_then_conflict() throws Exception {
        PurchaseOrderFixture fixture = createDraftPurchaseOrder(10);
        place(fixture.purchaseOrderId);
        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/lines/" + fixture.lineId
                        + "/receipts").with(authenticatedAsManager)
                .contentType("application/json")
                .content("{\"quantity\":10}"));

        mockMvc.perform(post("/api/v1/purchase-orders/" + fixture.purchaseOrderId + "/cancellation")
                        .with(authenticatedAsManager))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PURCHASE_ORDER_NOT_CANCELLABLE")));
    }

    private void place(String purchaseOrderId) throws Exception {
        mockMvc.perform(post("/api/v1/purchase-orders/" + purchaseOrderId + "/placement").with(authenticatedAsManager));
    }

    private PurchaseOrderFixture createDraftPurchaseOrder(int quantityOrdered) throws Exception {
        String supplierId = createSupplier();
        String warehouseId = createWarehouse();
        String productId = createProduct();

        MvcResult createResult = mockMvc
                .perform(post("/api/v1/purchase-orders").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content(purchaseOrderJson(supplierId, warehouseId, productId, quantityOrdered, "5.00")))
                .andReturn();
        String purchaseOrderId = idFromLocation(createResult);
        String lineId = firstLineId(createResult);

        return new PurchaseOrderFixture(purchaseOrderId, lineId, productId, warehouseId);
    }

    private String purchaseOrderJson(String supplierId, String warehouseId, String productId, int quantity, String unitCost) {
        return "{\"supplierId\":\"" + supplierId + "\",\"warehouseId\":\"" + warehouseId
                + "\",\"lines\":[{\"productId\":\"" + productId + "\",\"quantityOrdered\":" + quantity
                + ",\"unitCost\":" + unitCost + "}]}";
    }

    private String createSupplier() throws Exception {
        return idFromLocation(mockMvc
                .perform(post("/api/v1/suppliers").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Supplier " + UUID.randomUUID() + "\"}"))
                .andReturn());
    }

    private String createWarehouse() throws Exception {
        return idFromLocation(mockMvc
                .perform(post("/api/v1/warehouses").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Warehouse " + UUID.randomUUID() + "\"}"))
                .andReturn());
    }

    private String createProduct() throws Exception {
        String categoryId = idFromLocation(mockMvc
                .perform(post("/api/v1/categories").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"name\":\"Electronics\"}"))
                .andReturn());

        return idFromLocation(mockMvc
                .perform(post("/api/v1/products").with(authenticatedAsManager)
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"categoryId\":\"" + categoryId
                                + "\",\"unitOfMeasure\":\"EACH\"}"))
                .andReturn());
    }

    private String idFromLocation(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String firstLineId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return JsonPath.read(body, "$.lines[0].id");
    }

    private record PurchaseOrderFixture(String purchaseOrderId, String lineId, String productId, String warehouseId) {
    }
}
