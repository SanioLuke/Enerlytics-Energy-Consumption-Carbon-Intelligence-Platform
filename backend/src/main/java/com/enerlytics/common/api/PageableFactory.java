package com.enerlytics.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds Spring {@link Pageable} instances while rejecting unknown sort fields.
 */
public final class PageableFactory {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private PageableFactory() {
    }

    public static Pageable create(Integer page, Integer size, String[] sort, Set<String> allowedFields) {
        int requestedPage = page == null ? DEFAULT_PAGE : Math.max(0, page);
        int requestedSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(1, size), MAX_SIZE);

        Sort resolvedSort = Sort.unsorted();
        if (sort != null && sort.length > 0) {
            Sort.Order[] orders = new Sort.Order[sort.length / 2 + sort.length % 2];
            // simplistic: expect pairs "field,direction"
            int idx = 0;
            for (int i = 0; i < sort.length; i += 2) {
                String property = sort[i];
                String direction = (i + 1 < sort.length) ? sort[i + 1] : "asc";
                if (!allowedFields.contains(property)) {
                    throw new IllegalArgumentException("Invalid sort field: " + property);
                }
                orders[idx++] = new Sort.Order(parseDirection(direction), property);
            }
            if (idx > 0) {
                resolvedSort = Sort.by(java.util.Arrays.copyOf(orders, idx));
            }
        }
        return PageRequest.of(requestedPage, requestedSize, resolvedSort);
    }

    private static Sort.Direction parseDirection(String direction) {
        return "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
