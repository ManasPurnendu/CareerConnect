package com.careerconnect.common.pagination;

import com.careerconnect.common.response.PaginationMetadata;

import java.util.Collections;
import java.util.List;

public class PageResult<T> {

    private final List<T> items;
    private final PaginationMetadata pagination;

    public PageResult(List<T> items, PaginationMetadata pagination) {
        this.items = items != null ? items : Collections.emptyList();
        this.pagination = pagination;
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        return new PageResult<>(items, PaginationMetadata.of(page, size, totalElements));
    }

    public List<T> getItems() {
        return items;
    }

    public PaginationMetadata getPagination() {
        return pagination;
    }
}
