package com.careerconnect.common.util;

import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;

public final class JdbcUtils {

    private JdbcUtils() {
    }

    public static Long extractGeneratedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            for (Map.Entry<String, Object> entry : keys.entrySet()) {
                if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) {
                    return number.longValue();
                }
            }
            for (Object value : keys.values()) {
                if (value instanceof Number number) {
                    return number.longValue();
                }
            }
        }

        Number singleKey = keyHolder.getKey();
        if (singleKey != null) {
            return singleKey.longValue();
        }

        throw new IllegalStateException("Failed to retrieve generated primary key from database.");
    }
}
