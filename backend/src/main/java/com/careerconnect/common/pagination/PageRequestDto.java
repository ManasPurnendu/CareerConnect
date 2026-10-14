package com.careerconnect.common.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public class PageRequestDto {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    @PositiveOrZero(message = "Page must be greater than or equal to 0")
    private int page = DEFAULT_PAGE;

    @Max(value = MAX_SIZE, message = "Page size cannot exceed 100")
    private int size = DEFAULT_SIZE;

    public PageRequestDto() {
    }

    public PageRequestDto(Integer page, Integer size) {
        if (page != null && page >= 0) {
            this.page = page;
        }
        if (size != null && size > 0) {
            this.size = Math.min(size, MAX_SIZE);
        }
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        if (page >= 0) {
            this.page = page;
        }
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size > 0) {
            this.size = Math.min(size, MAX_SIZE);
        }
    }

    public int getOffset() {
        return page * size;
    }
}
