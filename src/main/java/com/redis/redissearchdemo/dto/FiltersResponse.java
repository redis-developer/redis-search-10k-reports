package com.redis.redissearchdemo.dto;

import java.util.Set;

public record FiltersResponse(
        Set<String> sectors,
        Set<String> sections
) {
}
