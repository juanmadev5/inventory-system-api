package com.jm.inventory.application.common;

import java.util.List;

/**
 * The one pagination envelope shape used by every endpoint in this API, regardless of
 * feature - a client that has learned one paginated endpoint can rely on every other one
 * having the same shape. {@code page} is 1-based; Spring Data's 0-based {@code Pageable}
 * is only ever used internally, never leaked into this envelope.
 */
public record PageResponse<T>(List<T> items, int page, int pageSize, long totalCount, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long totalCount) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        return new PageResponse<>(items, page, pageSize, totalCount, totalPages);
    }
}
