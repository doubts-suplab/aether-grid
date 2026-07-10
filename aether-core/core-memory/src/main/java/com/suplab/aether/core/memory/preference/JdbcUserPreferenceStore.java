package com.suplab.aether.core.memory.preference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

/**
 * JDBC implementation of {@link UserPreferenceStore} backed by the
 * {@code user_preferences} table (one JSONB document per user).
 */
public class JdbcUserPreferenceStore implements UserPreferenceStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserPreferenceStore.class);
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcUserPreferenceStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> find(String userId) {
        var sql = "SELECT preferences FROM user_preferences WHERE user_id = :userId";
        var rows = jdbc.query(sql, new MapSqlParameterSource("userId", userId),
                (rs, row) -> rs.getString("preferences"));
        if (rows.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rows.getFirst(), STRING_OBJECT_MAP);
        } catch (JsonProcessingException e) {
            log.warn("Malformed preferences JSON for userId={}, returning empty map: {}", userId, e.getMessage());
            return Map.of();
        }
    }

    @Override
    public void save(String userId, Map<String, Object> preferences) {
        var sql = """
                INSERT INTO user_preferences (user_id, preferences, updated_at)
                VALUES (:userId, :preferences::jsonb, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    preferences = EXCLUDED.preferences,
                    updated_at = NOW()
                """;
        String json;
        try {
            json = objectMapper.writeValueAsString(preferences);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise preferences", e);
        }
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("preferences", json);
        jdbc.update(sql, params);
        log.debug("Saved {} preference(s) for userId={}", preferences.size(), userId);
    }
}
