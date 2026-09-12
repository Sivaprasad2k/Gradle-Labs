package com.siva.jobscheduler.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> allItems, int page, int size) {
        if (size <= 0) size = 20;
        if (page < 0) page = 0;

        int totalElements = allItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<T> pageContent = allItems.subList(fromIndex, toIndex);
        return new PageResponse<>(pageContent, page, size, totalElements, totalPages);
    }
}
