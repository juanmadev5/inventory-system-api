package com.jm.inventory.application.common;

/**
 * Shared pagination constants - defined once and reused by every paginated endpoint,
 * never redefined per feature.
 */
public final class PaginationDefaults {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int FIRST_PAGE = 1;

    private PaginationDefaults() {
    }

    public static int normalizePage(Integer requestedPage) {
        return requestedPage == null || requestedPage < FIRST_PAGE ? FIRST_PAGE : requestedPage;
    }

    public static int normalizePageSize(Integer requestedPageSize) {
        if (requestedPageSize == null || requestedPageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedPageSize, MAX_PAGE_SIZE);
    }
}
