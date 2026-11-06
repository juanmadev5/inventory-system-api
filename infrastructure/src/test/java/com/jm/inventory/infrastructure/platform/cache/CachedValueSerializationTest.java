package com.jm.inventory.infrastructure.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jm.inventory.domain.catalog.Category;
import com.jm.inventory.domain.catalog.Product;
import com.jm.inventory.domain.catalog.UnitOfMeasure;
import com.jm.inventory.domain.warehouse.Warehouse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * The domain classes are cached as-is (Lombok {@code @Value}, no Jackson annotations), so
 * this pins down that they round-trip, and that a stored shape that no longer matches the
 * class is rejected - which {@link FailOpenCache} turns into a reload - rather than read
 * back with a silently defaulted field.
 */
class CachedValueSerializationTest {

    @Test
    void given_aProduct_when_serializedAndRead_then_itRoundTripsUnchanged() {
        // Given
        Product product = new Product(UUID.randomUUID(), "SKU-1", "Widget", null, UUID.randomUUID(),
                UnitOfMeasure.KG, false);
        JacksonJsonRedisSerializer<Product> serializer = serializerFor(Product.class);

        // When
        Product readBack = serializer.deserialize(serializer.serialize(product));

        // Then
        assertThat(readBack).isEqualTo(product);
    }

    @Test
    void given_aWarehouseAndACategory_when_serializedAndRead_then_bothRoundTripUnchanged() {
        // Given
        Warehouse warehouse = new Warehouse(UUID.randomUUID(), "Main", "Somewhere 1", true);
        Category category = new Category(UUID.randomUUID(), "Electronics", null);
        JacksonJsonRedisSerializer<Warehouse> warehouseSerializer = serializerFor(Warehouse.class);
        JacksonJsonRedisSerializer<Category> categorySerializer = serializerFor(Category.class);

        // When
        Warehouse warehouseReadBack = warehouseSerializer.deserialize(warehouseSerializer.serialize(warehouse));
        Category categoryReadBack = categorySerializer.deserialize(categorySerializer.serialize(category));

        // Then
        assertThat(warehouseReadBack).isEqualTo(warehouse);
        assertThat(categoryReadBack).isEqualTo(category);
    }

    @Test
    void given_aStoredValueMissingAField_when_read_then_itIsRejected() {
        // Given
        byte[] olderShape = ("{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Main\",\"address\":null}").getBytes();

        // When / Then
        assertThatThrownBy(() -> serializerFor(Warehouse.class).deserialize(olderShape))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void given_aStoredValueWithAnUnknownField_when_read_then_itIsRejected() {
        // Given
        byte[] newerShape = ("{\"id\":\"" + UUID.randomUUID()
                + "\",\"name\":\"Main\",\"address\":null,\"active\":true,\"region\":\"EU\"}").getBytes();

        // When / Then
        assertThatThrownBy(() -> serializerFor(Warehouse.class).deserialize(newerShape))
                .isInstanceOf(SerializationException.class);
    }

    private static <T> JacksonJsonRedisSerializer<T> serializerFor(Class<T> valueType) {
        return new JacksonJsonRedisSerializer<>(RedisCacheConfig.STRICT_JSON_MAPPER, valueType);
    }
}
