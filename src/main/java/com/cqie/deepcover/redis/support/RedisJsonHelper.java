package com.cqie.deepcover.redis.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class RedisJsonHelper {
    private final ObjectMapper objectMapper;

    public RedisJsonHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Redis value.", exception);
        }
    }

    public <T> T read(String json, Class<T> type, String key) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize Redis value: " + key, exception);
        }
    }
}
