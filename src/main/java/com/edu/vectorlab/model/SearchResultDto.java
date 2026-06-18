package com.edu.vectorlab.model;

import java.util.Map;

public record SearchResultDto(String content, Double score, Map<String, Object> metadata) {
}
