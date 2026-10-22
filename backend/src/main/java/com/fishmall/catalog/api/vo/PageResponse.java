package com.fishmall.catalog.api.vo;

import java.util.List;

public record PageResponse<T>(long total, int page, int pageSize, List<T> items) {
}
