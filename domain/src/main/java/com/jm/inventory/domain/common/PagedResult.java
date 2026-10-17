package com.jm.inventory.domain.common;

import java.util.List;

/**
 * A repository's raw page of results - deliberately not the API's {@code PageResponse}
 * envelope (that's an application/wire-level concern with {@code totalPages} etc.;
 * {@code domain} stays unaware of it).
 */
public record PagedResult<T>(List<T> items, long totalCount) {
}
