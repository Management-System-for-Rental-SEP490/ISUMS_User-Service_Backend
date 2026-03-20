package com.isums.userservice.domains.dtos;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {}
